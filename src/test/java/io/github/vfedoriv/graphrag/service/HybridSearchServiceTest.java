package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.dto.HybridSearchRequest;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.neo4j.core.Neo4jClient;

class HybridSearchServiceTest {

    @Test
    void rejectsTopKAboveConfiguredLimit() {
        HybridSearchService service = new HybridSearchService(
            settings(),
            provider(texts -> List.of(vector())),
            org.mockito.Mockito.mock(Neo4jClient.class),
            knowledgeBaseService(),
            emptyChunkRepository()
        );

        assertThatThrownBy(() -> service.search("kb-1", new HybridSearchRequest("contracts", 51, 1, true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("topK");
    }

    @Test
    void rejectsGraphDepthAboveConfiguredLimit() {
        HybridSearchService service = new HybridSearchService(
            settings(),
            provider(texts -> List.of(vector())),
            org.mockito.Mockito.mock(Neo4jClient.class),
            knowledgeBaseService(),
            emptyChunkRepository()
        );

        assertThatThrownBy(() -> service.search("kb-1", new HybridSearchRequest("contracts", 10, 3, true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("graphDepth");
    }

    @Test
    void failsClearlyWhenEmbeddingClientIsMissing() {
        HybridSearchService service = new HybridSearchService(
            settings(),
            provider(),
            org.mockito.Mockito.mock(Neo4jClient.class),
            knowledgeBaseService(),
            emptyChunkRepository()
        );

        assertThatThrownBy(() -> service.search("kb-1", new HybridSearchRequest("contracts", 10, 1, true)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Embedding model is not configured");
    }

    @Test
    void enrichesRankedHitsWithOneBoundedRelationalBatchAndDropsStaleDocuments() {
        DocumentUploadRepository documentRepository = Mockito.mock(DocumentUploadRepository.class);
        DocumentUploadNode firstDocument = document("doc-1", "first.txt");
        DocumentUploadNode secondDocument = document("doc-2", "second.txt");
        Mockito.when(documentRepository.findAllByIdInAndKnowledgeBaseId(
            List.of("doc-2", "missing", "doc-1"),
            "kb-1"
        )).thenReturn(List.of(firstDocument, secondDocument));
        Neo4jClient neo4jClient = Mockito.mock(Neo4jClient.class);
        HybridSearchService service = new HybridSearchService(
            settings(),
            Mockito.mock(ProfileScopedAiClientResolver.class),
            neo4jClient,
            knowledgeBaseService(),
            Mockito.mock(EmbeddingSpacePolicy.class),
            new EmbeddingSpaceIndexService(neo4jClient),
            documentRepository
        );

        List<io.github.vfedoriv.graphrag.dto.HybridSearchHit> hits = service.enrichHits(
            "kb-1",
            List.of(
                row("chunk-2", "doc-2", 0.9),
                row("chunk-stale", "missing", 0.8),
                row("chunk-1", "doc-1", 0.7)
            ),
            false
        );

        assertThat(hits).extracting(hit -> hit.chunkId()).containsExactly("chunk-2", "chunk-1");
        assertThat(hits).extracting(hit -> hit.source().originalFilename()).containsExactly("second.txt", "first.txt");
        Mockito.verify(documentRepository).findAllByIdInAndKnowledgeBaseId(
            List.of("doc-2", "missing", "doc-1"),
            "kb-1"
        );
    }

    private AppProperties props() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 3, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 80, 4000),
            new AppProperties.Query(200, 15, true, List.of("CREATE"), 10, 50, 4, 200, 1, 2, true),
            new AppProperties.Extraction(40, 80, 2)
        );
    }

    private RuntimeSettingsService settings() {
        return TestRuntimeSettings.from(props());
    }

    private KnowledgeBaseService knowledgeBaseService() {
        KnowledgeBaseService service = Mockito.mock(KnowledgeBaseService.class);
        Mockito.when(service.activeAiProfile("kb-1")).thenReturn(profile());
        return service;
    }

    private DocumentChunkRepository emptyChunkRepository() {
        DocumentChunkRepository repository = Mockito.mock(DocumentChunkRepository.class);
        Mockito.when(repository.findEmbeddedChunksByKnowledgeBaseId("kb-1")).thenReturn(List.of());
        return repository;
    }

    private AiProfileNode profile() {
        AiProfileNode profile = new AiProfileNode();
        profile.setId(AiProfileService.DEFAULT_PROFILE_ID);
        profile.setBaseUrl("https://api.openai.com/v1");
        profile.setEmbeddingModel("text-embedding-3-small");
        profile.setEmbeddingDimensions(3);
        return profile;
    }

    private DocumentUploadNode document(String id, String filename) {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId(id);
        document.setOriginalFilename(filename);
        document.setContentType("text/plain");
        document.setSizeBytes(10);
        return document;
    }

    private Map<String, Object> row(String chunkId, String documentId, double score) {
        return Map.of(
            "chunkId", chunkId,
            "documentId", documentId,
            "chunkIndex", 0,
            "score", score,
            "chunkMetadata", "{}",
            "entities", List.of(),
            "relationships", List.of()
        );
    }

    private List<Double> vector() {
        return List.of(0.1, 0.2, 0.3);
    }

    private ObjectProvider<EmbeddingClient> provider(EmbeddingClient... clients) {
        return new ObjectProvider<>() {
            @Override
            public EmbeddingClient getObject(Object... args) throws BeansException {
                return clients.length == 0 ? null : clients[0];
            }

            @Override
            public EmbeddingClient getIfAvailable() throws BeansException {
                return clients.length == 0 ? null : clients[0];
            }

            @Override
            public EmbeddingClient getIfUnique() throws BeansException {
                return clients.length == 1 ? clients[0] : null;
            }

            @Override
            public EmbeddingClient getObject() throws BeansException {
                return clients.length == 0 ? null : clients[0];
            }

            @Override
            public Stream<EmbeddingClient> orderedStream() {
                return Stream.of(clients);
            }
        };
    }
}
