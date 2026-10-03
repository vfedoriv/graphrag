package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.MetadataConstraints;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.ai.models.EmbeddingClient;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.indexes.contracts.LexicalIndexRepository;
import io.github.vfedoriv.graphrag.search.retrieval.application.DenseTextRetriever;
import io.github.vfedoriv.graphrag.search.retrieval.application.DocumentMetadataTextRetriever;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingSpace;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingSpaceIdentity;
import io.github.vfedoriv.graphrag.indexes.contracts.VectorIndexes;
import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseLifecycleService;
import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService;
import io.github.vfedoriv.graphrag.search.retrieval.application.LexicalTextRetriever;
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
@Import({TestcontainersConfiguration.class, AdvancedSearchTextRetrievalIntegrationTest.FakeEmbeddingConfig.class})
@TestPropertySource(properties = "app.model.embedding-dimensions=3")
@IntegrationTest
class AdvancedSearchTextRetrievalIntegrationTest {

    @Autowired private DenseTextRetriever denseTextRetriever;
    @Autowired private LexicalTextRetriever lexicalTextRetriever;
    @Autowired private DocumentMetadataTextRetriever metadataTextRetriever;
    @Autowired private Neo4jClient neo4jClient;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    @Autowired private KnowledgeBaseService knowledgeBaseService;
    @Autowired private VectorIndexes embeddingSpaceIndexService;
    @Autowired private LexicalIndexRepository lexicalIndexRepository;
    @Autowired private DocumentUploadRepository documentUploadRepository;

    private EmbeddingSpace embeddingSpace;

    @BeforeEach
    void setUp() {
        neo4jClient.query("MATCH (node) DETACH DELETE node").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        knowledgeBaseLifecycleService.provision("kb-1", "KB 1");
        knowledgeBaseLifecycleService.provision("kb-2", "KB 2");
        saveDocument("doc-1", "kb-1", "contract.txt", "text/plain", "1");
        saveDocument("doc-2", "kb-1", "notes.md", "text/markdown", "2");
        saveDocument("doc-other", "kb-2", "contract.txt", "text/plain", "3");
        embeddingSpace = EmbeddingSpaceIdentity.derive(
            "https://api.openai.com/v1", "text-embedding-3-small", 3
        );
        createLegacyChild("chunk-exact", "kb-1", "doc-1", 0, "ACME-42 renewal clause", List.of(0.95, 0.05, 0.0));
        createLegacyChild("chunk-near", "kb-1", "doc-2", 0, "ACME renewal guidance", List.of(0.80, 0.20, 0.0));
        createLegacyChild("chunk-other-best", "kb-2", "doc-other", 0, "ACME-42 renewal clause exact", List.of(1.0, 0.0, 0.0));
        assignVectorSpace("chunk-exact", "kb-1");
        assignVectorSpace("chunk-near", "kb-1");
        assignVectorSpace("chunk-other-best", "kb-2");
        embeddingSpaceIndexService.ensureIndex("kb-1", embeddingSpace);
        embeddingSpaceIndexService.ensureIndex("kb-2", embeddingSpace);
        awaitIndex(embeddingSpaceIndexService.indexName("kb-1", embeddingSpace.id()));
        awaitIndex(embeddingSpaceIndexService.indexName("kb-2", embeddingSpace.id()));
    }

    @Test
    void denseRankingUsesOneKnowledgeBaseEmbeddingSpaceBeforeApplyingCandidateLimit() {
        Result result = denseTextRetriever.retrieve(request(
            List.of(new Subquery("q-1", "dense query")),
            new MetadataConstraints(null, null),
            2
        ));

        assertThat(result.diagnostics().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.candidates()).extracting(candidate -> candidate.source().chunkId())
            .containsExactly("chunk-exact", "chunk-near")
            .doesNotContain("chunk-other-best");
        assertThat(result.candidates()).extracting(candidate -> candidate.rank()).containsExactly(1, 2);
    }

    @Test
    void lexicalRankingBackfillsLegacyChildrenAndWaitsForScopedIndexReadiness() {
        Result result = lexicalTextRetriever.retrieve(request(
            List.of(new Subquery("q-1", "ACME-42 renewal")),
            new MetadataConstraints(null, null),
            2
        ));

        assertThat(result.diagnostics().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.candidates()).extracting(candidate -> candidate.source().chunkId())
            .contains("chunk-exact")
            .doesNotContain("chunk-other-best");
        assertThat(result.candidates()).extracting(candidate -> candidate.text())
            .contains("ACME-42 renewal clause");
        Long backfilled = neo4jClient.query("""
            MATCH (chunk:DocumentChunk:%s {knowledgeBaseId: 'kb-1'})
            WHERE chunk.sourceText = chunk.text
            RETURN count(chunk) AS count
            """.formatted(lexicalIndexRepository.labelName("kb-1")))
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(backfilled).isEqualTo(2L);
        String state = neo4jClient.query("""
            SHOW FULLTEXT INDEXES YIELD name, state
            WHERE name = $name
            RETURN state
            """)
            .bind(lexicalIndexRepository.indexName("kb-1")).to("name")
            .fetchAs(String.class).one().orElse("MISSING");
        assertThat(state).isEqualTo("ONLINE");
    }

    @Test
    void metadataLookupUsesOwnedRelationalRowsBeforeBoundedChunkLoading() {
        Result result = metadataTextRetriever.retrieve(request(
            List.of(),
            new MetadataConstraints("contract.txt", "text/plain"),
            3
        ));

        assertThat(result.diagnostics().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.candidates()).extracting(candidate -> candidate.source().documentId())
            .containsOnly("doc-1");
        assertThat(result.candidates()).extracting(candidate -> candidate.source().chunkId())
            .containsExactly("chunk-exact");
    }

    @Test
    void deletingKnowledgeBaseDropsItsLazyLexicalIndex() {
        knowledgeBaseLifecycleService.provision("kb-delete", "KB delete");
        lexicalIndexRepository.ensureOnline("kb-delete", Instant.now().plusSeconds(30));

        knowledgeBaseService.delete("kb-delete");

        Long indexCount = neo4jClient.query("""
            SHOW FULLTEXT INDEXES YIELD name
            WHERE name = $name
            RETURN count(*) AS count
            """)
            .bind(lexicalIndexRepository.indexName("kb-delete")).to("name")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(indexCount).isZero();
    }

    private Request request(List<Subquery> subqueries, MetadataConstraints metadata, int limit) {
        return new Request("kb-1", subqueries, metadata, limit, true, Instant.now().plusSeconds(30));
    }

    private void createLegacyChild(
        String id,
        String knowledgeBaseId,
        String documentId,
        int chunkIndex,
        String text,
        List<Double> embedding
    ) {
        neo4jClient.query("""
            CREATE (chunk:DocumentChunk {
              id: $id,
              kind: 'CHILD',
              knowledgeBaseId: $knowledgeBaseId,
              documentId: $documentId,
              chunkIndex: $chunkIndex,
              text: $text,
              embedding: $embedding,
              embeddingSpaceId: $embeddingSpaceId,
              embeddingModel: $embeddingModel,
              embeddingDimensions: 3,
              tokenizerId: $tokenizerId,
              processingRunId: 'run-1',
              effectiveChunkerRevision: 'chunker-v1'
            })
            """)
            .bind(id).to("id")
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(documentId).to("documentId")
            .bind(chunkIndex).to("chunkIndex")
            .bind(text).to("text")
            .bind(embedding).to("embedding")
            .bind(embeddingSpace.id()).to("embeddingSpaceId")
            .bind(embeddingSpace.model()).to("embeddingModel")
            .bind(embeddingSpace.tokenizerId()).to("tokenizerId")
            .run();
    }

    private void assignVectorSpace(String chunkId, String knowledgeBaseId) {
        embeddingSpaceIndexService.assignChunk(chunkId, knowledgeBaseId, embeddingSpace);
    }

    private void awaitIndex(String name) {
        neo4jClient.query("CALL db.awaitIndex($name)").bind(name).to("name").run();
    }

    private void saveDocument(
        String id,
        String knowledgeBaseId,
        String filename,
        String contentType,
        String digestSeed
    ) {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId(id);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setOriginalFilename(filename);
        document.setContentType(contentType);
        document.setSizeBytes(100);
        document.setSha256(digestSeed.repeat(64));
        document.setStatus(DocumentStatus.COMPLETED);
        document.setUploadedAt(Instant.now());
        documentUploadRepository.save(document);
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean
        EmbeddingClient embeddingClient() {
            return texts -> texts.stream().map(ignored -> List.of(1.0, 0.0, 0.0)).toList();
        }
    }
}
