package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.dto.HybridSearchRequest;
import io.github.vfedoriv.graphrag.dto.HybridSearchResponse;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIdentity;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIndexService;
import io.github.vfedoriv.graphrag.service.HybridSearchService;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseLifecycleService;
import java.util.Comparator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({TestcontainersConfiguration.class, HybridSearchIntegrationTest.FakeEmbeddingConfig.class})
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration",
    "app.model.embedding-dimensions=3",
    "app.query.hybrid-search-default-top-k=2",
    "app.query.hybrid-search-max-top-k=5",
    "app.query.hybrid-search-candidate-multiplier=4",
    "app.query.hybrid-search-max-candidates=10",
    "app.query.hybrid-search-default-graph-depth=1",
    "app.query.hybrid-search-max-graph-depth=2",
    "app.query.hybrid-search-include-chunk-text=true"
})
class HybridSearchIntegrationTest {

    @Autowired
    private HybridSearchService hybridSearchService;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private EmbeddingSpaceIndexService embeddingSpaceIndexService;
    @Autowired
    private KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    @Autowired
    private DocumentUploadRepository documentUploadRepository;

    @BeforeEach
    void setUpGraph() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        knowledgeBaseLifecycleService.provision("kb-1", "KB 1");
        knowledgeBaseLifecycleService.provision("kb-2", "KB 2");
        knowledgeBaseLifecycleService.provision("kb-empty", "Empty KB");
        saveDocument("doc-1", "kb-1", "contract-a.txt", 100, "1");
        saveDocument("doc-2", "kb-1", "contract-b.txt", 110, "2");
        saveDocument("doc-3", "kb-1", "maintenance.txt", 120, "3");
        saveDocument("doc-4", "kb-2", "other-kb.txt", 130, "4");
        neo4jClient.query("""
            CREATE (exact:DocumentChunk {id: 'chunk-renewal-exact', knowledgeBaseId: 'kb-1', documentId: 'doc-1', chunkIndex: 0, text: 'Acme renewal terms exact match', metadata: '{"source":"contract-a.txt","section":"renewal"}', embedding: [1.0, 0.0, 0.0]})
            CREATE (near:DocumentChunk {id: 'chunk-renewal-near', knowledgeBaseId: 'kb-1', documentId: 'doc-1', chunkIndex: 1, text: 'Acme renewal notice and extension terms', metadata: '{"source":"contract-a.txt","section":"notice"}', embedding: [0.95, 0.05, 0.0]})
            CREATE (related:DocumentChunk {id: 'chunk-service-related', knowledgeBaseId: 'kb-1', documentId: 'doc-2', chunkIndex: 0, text: 'Supplier service renewal obligations', metadata: '{"source":"contract-b.txt","section":"service"}', embedding: [0.8, 0.2, 0.0]})
            CREATE (maintenance:DocumentChunk {id: 'chunk-maintenance-low', knowledgeBaseId: 'kb-1', documentId: 'doc-3', chunkIndex: 0, text: 'Pump maintenance schedule unrelated to renewal', metadata: '{"source":"maintenance.txt","section":"schedule"}', embedding: [0.2, 0.8, 0.0]})
            CREATE (noise:DocumentChunk {id: 'chunk-noise', knowledgeBaseId: 'kb-1', documentId: 'doc-3', chunkIndex: 1, text: 'Safety inspection checklist', metadata: '{"source":"maintenance.txt","section":"safety"}', embedding: [0.0, 1.0, 0.0]})
            CREATE (otherKbBest:DocumentChunk {id: 'chunk-other-kb-best', knowledgeBaseId: 'kb-2', documentId: 'doc-4', chunkIndex: 0, text: 'Other KB renewal exact match', metadata: '{"source":"other-kb.txt","section":"renewal"}', embedding: [1.0, 0.0, 0.0]})
            CREATE (otherKbNear:DocumentChunk {id: 'chunk-other-kb-near', knowledgeBaseId: 'kb-2', documentId: 'doc-4', chunkIndex: 1, text: 'Other KB renewal near match', metadata: '{"source":"other-kb.txt","section":"notice"}', embedding: [0.9, 0.1, 0.0]})
            CREATE (contract:Contract {id: 'contract-1', contractId: 'C-1', title: 'Master Agreement'})
            CREATE (party:Party {id: 'party-1', name: 'Acme'})
            CREATE (obligation:Obligation {id: 'obligation-1', summary: 'Renewal notice must be sent 30 days before expiry'})
            CREATE (exact)-[:MENTIONS]->(contract)
            CREATE (near)-[:MENTIONS]->(obligation)
            CREATE (related)-[:MENTIONS]->(party)
            CREATE (contract)-[:HAS_PARTY {role: 'Supplier'}]->(party)
            CREATE (contract)-[:HAS_OBLIGATION {kind: 'Renewal'}]->(obligation)
            """).run();
        EmbeddingSpace embeddingSpace = EmbeddingSpaceIdentity.derive(
            "https://api.openai.com/v1", "text-embedding-3-small", 3
        );
        assignSpace("kb-1", embeddingSpace);
        assignSpace("kb-2", embeddingSpace);
        embeddingSpaceIndexService.ensureIndex("kb-1", embeddingSpace);
        embeddingSpaceIndexService.ensureIndex("kb-2", embeddingSpace);
        neo4jClient.query("CALL db.awaitIndex($name)")
            .bind(embeddingSpaceIndexService.indexName("kb-1", embeddingSpace.id())).to("name")
            .run();
        neo4jClient.query("CALL db.awaitIndex($name)")
            .bind(embeddingSpaceIndexService.indexName("kb-2", embeddingSpace.id())).to("name")
            .run();
    }

    private void assignSpace(String knowledgeBaseId, EmbeddingSpace embeddingSpace) {
        neo4jClient.query("""
            MATCH (chunk:DocumentChunk {knowledgeBaseId: $knowledgeBaseId})
            SET chunk.embeddingSpaceId = $embeddingSpaceId
            SET chunk.embeddingModel = $embeddingModel
            SET chunk.tokenizerId = $tokenizerId
            SET chunk:%s
            """.formatted(embeddingSpaceIndexService.labelName(knowledgeBaseId, embeddingSpace.id())))
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(embeddingSpace.id()).to("embeddingSpaceId")
            .bind(embeddingSpace.model()).to("embeddingModel")
            .bind(embeddingSpace.tokenizerId()).to("tokenizerId")
            .run();
    }

    private void saveDocument(String id, String knowledgeBaseId, String filename, long sizeBytes, String digestSeed) {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId(id);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setOriginalFilename(filename);
        document.setContentType("text/plain");
        document.setSizeBytes(sizeBytes);
        document.setSha256(digestSeed.repeat(64));
        document.setStatus(DocumentStatus.COMPLETED);
        document.setUploadedAt(Instant.now());
        documentUploadRepository.save(document);
    }

    @Test
    void hybridSearchRanksKnowledgeBaseScopedChunksByVectorScore() {
        HybridSearchResponse response = hybridSearchService.search(
            "kb-1",
            new HybridSearchRequest("renewal terms", 3, 1, true)
        );

        assertThat(response.hitCount()).isEqualTo(3);
        assertThat(response.hits())
            .extracting(hit -> hit.chunkId())
            .containsExactly("chunk-renewal-exact", "chunk-renewal-near", "chunk-service-related");
        assertThat(response.hits())
            .extracting(hit -> hit.chunkId())
            .doesNotContain("chunk-other-kb-best", "chunk-other-kb-near", "chunk-maintenance-low", "chunk-noise");
        assertThat(response.hits())
            .extracting(hit -> hit.score())
            .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(response.hits().getFirst().source().originalFilename()).isEqualTo("contract-a.txt");
        assertThat(response.hits().getFirst().text()).isEqualTo("Acme renewal terms exact match");
        assertThat(response.hits().getFirst().graph().entities())
            .anySatisfy(entity -> assertThat(entity.labels()).contains("Contract"));
        assertThat(response.hits().getFirst().graph().relationships())
            .anySatisfy(relationship -> assertThat(relationship.type()).isEqualTo("HAS_PARTY"));
    }

    @Test
    void hybridSearchCanReturnLowerRankedTargetChunksWhenTopVectorHitsBelongToAnotherKnowledgeBase() {
        HybridSearchResponse response = hybridSearchService.search(
            "kb-1",
            new HybridSearchRequest("renewal terms", 4, 1, false)
        );

        assertThat(response.hitCount()).isEqualTo(4);
        assertThat(response.includeChunkText()).isFalse();
        assertThat(response.hits())
            .extracting(hit -> hit.chunkId())
            .containsExactly(
                "chunk-renewal-exact",
                "chunk-renewal-near",
                "chunk-service-related",
                "chunk-maintenance-low"
            );
        assertThat(response.hits())
            .extracting(hit -> hit.text())
            .containsOnlyNulls();
    }

    @Test
    void hybridSearchReturnsEmptyHitsForKnowledgeBaseWithoutMatches() {
        HybridSearchResponse response = hybridSearchService.search(
            "kb-empty",
            new HybridSearchRequest("renewal terms", 2, 1, true)
        );

        assertThat(response.hits()).isEmpty();
        assertThat(response.hitCount()).isZero();
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean
        EmbeddingClient embeddingClient() {
            return texts -> List.of(List.of(1.0, 0.0, 0.0));
        }
    }
}
