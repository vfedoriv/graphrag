package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import io.github.vfedoriv.graphrag.service.DocumentProcessingService;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIdentity;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIndexService;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DocumentProcessingIntegrationTest.FakeEmbeddingConfig.class})
@IntegrationTest
class DocumentProcessingIntegrationTest {

    @Autowired
    private DocumentUploadService documentUploadService;
    @Autowired
    private DocumentProcessingService documentProcessingService;
    @Autowired
    private DocumentChunkRepository documentChunkRepository;
    @Autowired
    private ExtractionRunRepository extractionRunRepository;
    @Autowired
    private DocumentProcessingRunRepository processingRunRepository;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private SchemaRegistryService schemaRegistryService;
    @Autowired
    private EmbeddingSpaceIndexService embeddingSpaceIndexService;

    @AfterEach
    void cleanDocumentStorage() throws Exception {
        TestDocumentStorage.clean();
    }

    @Test
    void persistsChunksCreatesVectorIndexAndSupportsVectorSearch() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        String schemaJson = """
            {
              "name": "contracts",
              "version": 1,
              "nodes": [
                {
                  "label": "Contract",
                  "key": "contractId",
                  "properties": [{"name": "contractId", "type": "string"}]
                },
                {
                  "label": "Party",
                  "key": "name",
                  "properties": [{"name": "name", "type": "string"}]
                }
              ],
              "relationships": [
                {"type": "HAS_PARTY", "from": "Contract", "to": "Party"}
              ]
            }
            """;
        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaJson, SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-1", schema.getId());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "contract.txt",
            "text/plain",
            "first chunk sentence. second chunk sentence.".getBytes()
        );
        DocumentUploadNode uploaded = documentUploadService.upload("kb-1", file);
        DocumentUploadNode processed = documentProcessingService.process(uploaded.getId());
        Throwable secondAttempt = catchThrowable(() -> documentProcessingService.process(uploaded.getId()));
        DocumentUploadNode processedAgain = documentProcessingService.process(uploaded.getId(), true);

        assertThat(processed.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(secondAttempt).isInstanceOf(ConflictException.class);
        assertThat(processedAgain.getStatus().name()).isEqualTo("COMPLETED");
        List<DocumentChunkNode> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(uploaded.getId());
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).extracting("chunkIndex").isSorted();
        assertThat(chunks.get(0).getEmbedding()).hasSize(1536);
        EmbeddingSpace embeddingSpace = EmbeddingSpaceIdentity.derive(
            "https://api.openai.com/v1", "text-embedding-3-small", 1536
        );
        assertThat(chunks).extracting(DocumentChunkNode::getEmbeddingSpaceId)
            .containsOnly(embeddingSpace.id());
        assertThat(chunks).extracting(DocumentChunkNode::getChunkStrategy)
            .containsOnly("fixed-character");
        assertThat(chunks).extracting(DocumentChunkNode::getTokenizerId)
            .containsOnly("cl100k_base");
        assertThat(chunks).extracting(DocumentChunkNode::getTokenCountMode)
            .containsOnly("EXACT");
        assertThat(chunks).extracting(DocumentChunkNode::getEffectiveChunkerRevision)
            .allMatch(revision -> revision != null && revision.startsWith("chunker_"));
        assertThat(chunks).extracting(DocumentChunkNode::getSourceStart)
            .allMatch(position -> position != null && position >= 0);
        List<DocumentProcessingRunNode> runHistory =
            processingRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId());
        assertThat(runHistory).hasSize(2);
        assertThat(runHistory).extracting(DocumentProcessingRunNode::getTokenizerId)
            .containsOnly("cl100k_base");
        assertThat(runHistory).extracting(DocumentProcessingRunNode::getEffectiveChunkerRevision)
            .allMatch(revision -> revision != null && revision.startsWith("chunker_"));

        Long indexCount = neo4jClient.query("""
            SHOW INDEXES YIELD name, type
            WHERE name = $name AND type = 'VECTOR'
            RETURN count(*) AS c
            """)
            .bind(embeddingSpaceIndexService.indexName("kb-1", embeddingSpace.id())).to("name")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(indexCount).isEqualTo(1L);

        Long hitCount = neo4jClient.query("""
            CALL db.index.vector.queryNodes($name, 3, $queryVector) YIELD node, score
            RETURN count(node) AS c
            """)
            .bind(embeddingSpaceIndexService.indexName("kb-1", embeddingSpace.id())).to("name")
            .bind(vectorOf(0.11)).to("queryVector")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(hitCount).isGreaterThan(0L);

        Long anyContract = neo4jClient.query("MATCH (n:Contract) RETURN count(n) AS c")
            .fetchAs(Long.class).one().orElse(0L);
        Long anyParty = neo4jClient.query("MATCH (n:Party) RETURN count(n) AS c")
            .fetchAs(Long.class).one().orElse(0L);
        Long relCount = neo4jClient.query("MATCH (:Contract)-[r:HAS_PARTY]->(:Party) RETURN count(r) AS c")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(anyContract).isEqualTo(1L);
        assertThat(anyParty).isEqualTo(1L);
        assertThat(relCount).isEqualTo(1L);

        Long nodeEvidence = neo4jClient.query("""
            MATCH (e:NodeExtractionEvidence {sourceDocumentId: $documentId})-[:ASSERTS_NODE]->(:Contract)
            WHERE e.schemaId IS NOT NULL AND e.extractionRunId IS NOT NULL
            RETURN count(e) AS c
            """)
            .bind(uploaded.getId()).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        Long relationshipEvidence = neo4jClient.query("""
            MATCH (e:RelationshipExtractionEvidence {sourceDocumentId: $documentId})
            WHERE e.schemaId IS NOT NULL AND e.extractionRunId IS NOT NULL
            RETURN count(e) AS c
            """)
            .bind(uploaded.getId()).to("documentId")
            .fetchAs(Long.class).one().orElse(0L);
        int extractionRuns = extractionRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId()).size();
        int processingRuns = processingRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId()).size();
        Long graphRunNodes = neo4jClient.query("""
            MATCH (run)
            WHERE run:ExtractionRun OR run:DocumentProcessingRun
            RETURN count(run) AS c
            """)
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(nodeEvidence).isEqualTo(1L);
        assertThat(relationshipEvidence).isEqualTo(1L);
        assertThat(extractionRuns).isEqualTo(2);
        assertThat(processingRuns).isEqualTo(2);
        assertThat(graphRunNodes).isZero();

        documentUploadService.replace(
            "kb-1",
            uploaded.getId(),
            new MockMultipartFile(
                "file",
                "replacement.txt",
                "text/plain",
                "replacement contract content".getBytes()
            )
        );
        assertThat(extractionRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId())).isEmpty();
        assertThat(processingRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId())).isEmpty();
        assertThat(documentProcessingService.process(uploaded.getId()).getStatus().name()).isEqualTo("COMPLETED");
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean
        EmbeddingClient embeddingClient() {
            return texts -> {
                List<List<Double>> out = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    out.add(vectorOf(0.11 + i));
                }
                return out;
            };
        }

        @Bean
        GraphExtractionClient graphExtractionClient() {
            return (schema, chunkText) -> new GraphExtractionResult(
                List.of(
                    new GraphExtractionResult.ExtractedNode(
                        "Contract",
                        java.util.Map.of("contractId", "C-1"),
                        0.95
                    ),
                    new GraphExtractionResult.ExtractedNode(
                        "Party",
                        java.util.Map.of("name", "Acme"),
                        0.90
                    )
                ),
                List.of(
                    new GraphExtractionResult.ExtractedRelationship(
                        "HAS_PARTY",
                        "Contract",
                        java.util.Map.of("contractId", "C-1"),
                        "Party",
                        java.util.Map.of("name", "Acme"),
                        java.util.Map.of("role", "Supplier"),
                        0.88
                    )
                )
            );
        }
    }

    private static List<Double> vectorOf(double base) {
        List<Double> vector = new ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) {
            vector.add(base + (i * 0.000001));
        }
        return vector;
    }

}
