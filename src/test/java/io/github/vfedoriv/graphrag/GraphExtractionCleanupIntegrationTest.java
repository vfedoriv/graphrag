package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.domain.ExtractionRunStatus;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import io.github.vfedoriv.graphrag.service.DocumentProcessingService;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({TestcontainersConfiguration.class, GraphExtractionCleanupIntegrationTest.RetryFailureThenSuccessConfig.class})
@IntegrationTest
class GraphExtractionCleanupIntegrationTest {

    @Autowired
    private DocumentUploadService documentUploadService;
    @Autowired
    private DocumentProcessingService documentProcessingService;
    @Autowired
    private SchemaRegistryService schemaRegistryService;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private ExtractionRunRepository extractionRunRepository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private RetryCounters retryCounters;

    @BeforeEach
    void resetIntegrationState() throws Exception {
        IntegrationTestLifecycle.reset(jdbcTemplate, neo4jClient, retryCounters);
    }

    @Test
    void removesFailedRunAndFailedOnlyOrphansAfterSuccessfulRetry() {
        String schemaJson = """
            {
              "name": "contracts-cleanup",
              "version": 1,
              "nodes": [
                {
                  "label": "Contract",
                  "key": "contractId",
                  "properties": [{"name": "contractId", "type": "string"}]
                },
                {
                  "label": "Party",
                  "key": "partyId",
                  "properties": [{"name": "partyId", "type": "string"}]
                }
              ],
              "relationships": [
                {"type": "HAS_PARTY", "from": "Contract", "to": "Party"}
              ]
            }
            """;
        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaJson, SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-cleanup", schema.getId());

        String longText = "a".repeat(4100);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "cleanup.txt",
            "text/plain",
            longText.getBytes()
        );
        DocumentUploadNode uploaded = documentUploadService.upload("kb-cleanup", file);

        DocumentUploadNode failed = documentProcessingService.process(uploaded.getId());
        assertThat(failed.getStatus().name()).isEqualTo("FAILED");

        DocumentUploadNode completed = documentProcessingService.process(uploaded.getId());
        assertThat(completed.getStatus().name()).isEqualTo("COMPLETED");

        int runCount = extractionRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId()).size();
        int failedRunCount = extractionRunRepository.findIdsByDocumentIdAndStatus(
            uploaded.getId(),
            ExtractionRunStatus.FAILED
        ).size();
        int completedRunCount = extractionRunRepository.findIdsByDocumentIdAndStatus(
            uploaded.getId(),
            ExtractionRunStatus.COMPLETED
        ).size();

        Long sharedNodeCount = neo4jClient.query("""
            MATCH (c:Contract {contractId: 'C-SHARED'})
            RETURN count(c) AS c
            """)
            .fetchAs(Long.class).one().orElse(0L);
        Long failedOnlyNodeCount = neo4jClient.query("""
            MATCH (c:Contract {contractId: 'C-FAILED-ONLY'})
            RETURN count(c) AS c
            """)
            .fetchAs(Long.class).one().orElse(0L);
        Long failedOnlyRelatedNodeCount = neo4jClient.query("""
            MATCH (p:Party {partyId: 'P-FAILED-ONLY'})
            RETURN count(p) AS c
            """)
            .fetchAs(Long.class).one().orElse(0L);
        Long failedOnlyRelationshipCount = neo4jClient.query("""
            MATCH (:Contract {contractId: 'C-FAILED-ONLY'})-[r:HAS_PARTY]->(:Party {partyId: 'P-FAILED-ONLY'})
            RETURN count(r) AS c
            """)
            .fetchAs(Long.class).one().orElse(0L);
        Long retainedRelationshipCount = neo4jClient.query("""
            MATCH (:Contract {contractId: 'C-SHARED'})-[r:HAS_PARTY]->(:Party {partyId: 'P-SHARED'})
            RETURN count(r) AS c
            """)
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(runCount).isEqualTo(2);
        assertThat(failedRunCount).isEqualTo(1);
        assertThat(completedRunCount).isEqualTo(1);
        assertThat(sharedNodeCount).isEqualTo(1L);
        assertThat(failedOnlyNodeCount).isEqualTo(0L);
        assertThat(failedOnlyRelatedNodeCount).isEqualTo(0L);
        assertThat(failedOnlyRelationshipCount).isEqualTo(0L);
        assertThat(retainedRelationshipCount).isEqualTo(1L);
    }

    @Test
    void removesMultipleFailedRunsAfterLaterSuccessfulRetry() {
        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaJson(), SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-cleanup", schema.getId());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "multi-fail.txt",
            "text/plain",
            "MULTI_FAIL scenario".getBytes()
        );
        DocumentUploadNode uploaded = documentUploadService.upload("kb-cleanup", file);

        DocumentUploadNode firstAttempt = documentProcessingService.process(uploaded.getId());
        DocumentUploadNode secondAttempt = documentProcessingService.process(uploaded.getId());
        DocumentUploadNode thirdAttempt = documentProcessingService.process(uploaded.getId());

        assertThat(firstAttempt.getStatus().name()).isEqualTo("FAILED");
        assertThat(secondAttempt.getStatus().name()).isEqualTo("FAILED");
        assertThat(thirdAttempt.getStatus().name()).isEqualTo("COMPLETED");

        int runCount = extractionRunRepository.findByDocumentIdOrderByStartedAtAsc(uploaded.getId()).size();
        int failedRunCount = extractionRunRepository.findIdsByDocumentIdAndStatus(
            uploaded.getId(),
            ExtractionRunStatus.FAILED
        ).size();
        int completedRunCount = extractionRunRepository.findIdsByDocumentIdAndStatus(
            uploaded.getId(),
            ExtractionRunStatus.COMPLETED
        ).size();

        assertThat(runCount).isEqualTo(3);
        assertThat(failedRunCount).isEqualTo(2);
        assertThat(completedRunCount).isEqualTo(1);
    }

    @Test
    void overwriteCleanupDeletesStaleCompletedRunArtifactsAndRelationships() {
        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaJson(), SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-cleanup", schema.getId());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "overwrite.txt",
            "text/plain",
            "OVERWRITE scenario".getBytes()
        );
        DocumentUploadNode uploaded = documentUploadService.upload("kb-cleanup", file);

        DocumentUploadNode initial = documentProcessingService.process(uploaded.getId());
        assertThat(initial.getStatus().name()).isEqualTo("COMPLETED");

        String staleRunId = extractionRunRepository.findIdsByDocumentIdAndStatus(
            uploaded.getId(),
            ExtractionRunStatus.COMPLETED
        ).getFirst();

        DocumentUploadNode overwritten = documentProcessingService.process(uploaded.getId(), true);
        assertThat(overwritten.getStatus().name()).isEqualTo("COMPLETED");

        Long staleEvidenceCount = neo4jClient.query("""
            MATCH (e:GraphExtractionEvidence {extractionRunId: $runId})
            RETURN count(e) AS c
            """)
            .bind(staleRunId).to("runId")
            .fetchAs(Long.class).one().orElse(0L);
        Long freshNodeCount = neo4jClient.query("""
            MATCH (:Contract {contractId: 'C-FRESH'})
            RETURN count(*) AS c
            """)
            .fetchAs(Long.class).one().orElse(0L);
        Long freshEdgeCount = neo4jClient.query("""
            MATCH (:Contract {contractId: 'C-FRESH'})-[r:HAS_PARTY]->(:Party {partyId: 'P-FRESH'})
            RETURN count(r) AS c
            """)
            .fetchAs(Long.class).one().orElse(0L);
        int completedRunCount = extractionRunRepository.findIdsByDocumentIdAndStatus(
            uploaded.getId(),
            ExtractionRunStatus.COMPLETED
        ).size();

        assertThat(staleEvidenceCount).isEqualTo(0L);
        assertThat(freshNodeCount).isEqualTo(1L);
        assertThat(freshEdgeCount).isEqualTo(1L);
        assertThat(completedRunCount).isEqualTo(2);
    }

    private String schemaJson() {
        return """
            {
              "name": "contracts-cleanup",
              "version": 1,
              "nodes": [
                {
                  "label": "Contract",
                  "key": "contractId",
                  "properties": [{"name": "contractId", "type": "string"}]
                },
                {
                  "label": "Party",
                  "key": "partyId",
                  "properties": [{"name": "partyId", "type": "string"}]
                }
              ],
              "relationships": [
                {"type": "HAS_PARTY", "from": "Contract", "to": "Party"}
              ]
            }
            """;
    }

    @TestConfiguration
    static class RetryFailureThenSuccessConfig {
        @Bean
        EmbeddingClient embeddingClient() {
            return texts -> {
                List<List<Double>> out = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    out.add(vectorOf(0.41 + i));
                }
                return out;
            };
        }

        @Bean
        RetryCounters retryCounters() {
            return new RetryCounters();
        }

        @Bean
        GraphExtractionClient graphExtractionClient(RetryCounters counters) {
            return (schema, chunkText) -> {
                if (chunkText.contains("MULTI_FAIL")) {
                    int multiFailCall = counters.multiFailCallCount.incrementAndGet();
                    if (multiFailCall <= 2) {
                        throw new IllegalStateException("Synthetic extraction failure for multi-fail retry path");
                    }
                    return new GraphExtractionResult(
                        List.of(new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-MULTI-SHARED"), 0.99)),
                        List.of()
                    );
                }
                if (chunkText.contains("OVERWRITE")) {
                    int overwriteCall = counters.overwriteCallCount.incrementAndGet();
                    if (overwriteCall == 1) {
                        return new GraphExtractionResult(
                            List.of(
                                new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-STALE"), 0.95),
                                new GraphExtractionResult.ExtractedNode("Party", Map.of("partyId", "P-STALE"), 0.95)
                            ),
                            List.of(
                                new GraphExtractionResult.ExtractedRelationship(
                                    "HAS_PARTY",
                                    "Contract",
                                    Map.of("contractId", "C-STALE"),
                                    "Party",
                                    Map.of("partyId", "P-STALE"),
                                    Map.of(),
                                    0.95
                                )
                            )
                        );
                    }
                    return new GraphExtractionResult(
                        List.of(
                            new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-FRESH"), 0.99),
                            new GraphExtractionResult.ExtractedNode("Party", Map.of("partyId", "P-FRESH"), 0.99)
                        ),
                        List.of(
                            new GraphExtractionResult.ExtractedRelationship(
                                "HAS_PARTY",
                                "Contract",
                                Map.of("contractId", "C-FRESH"),
                                "Party",
                                Map.of("partyId", "P-FRESH"),
                                Map.of(),
                                0.95
                            )
                        )
                    );
                }
                int call = counters.defaultCallCount.incrementAndGet();
                if (call == 2) {
                    throw new IllegalStateException("Synthetic extraction failure for retry path");
                }
                if (call == 1) {
                    return new GraphExtractionResult(
                        List.of(
                            new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-SHARED"), 0.95),
                            new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-FAILED-ONLY"), 0.90),
                            new GraphExtractionResult.ExtractedNode("Party", Map.of("partyId", "P-FAILED-ONLY"), 0.90)
                        ),
                        List.of(
                            new GraphExtractionResult.ExtractedRelationship(
                                "HAS_PARTY",
                                "Contract",
                                Map.of("contractId", "C-FAILED-ONLY"),
                                "Party",
                                Map.of("partyId", "P-FAILED-ONLY"),
                                Map.of(),
                                0.90
                            )
                        )
                    );
                }
                return new GraphExtractionResult(
                    List.of(
                        new GraphExtractionResult.ExtractedNode("Contract", Map.of("contractId", "C-SHARED"), 0.99),
                        new GraphExtractionResult.ExtractedNode("Party", Map.of("partyId", "P-SHARED"), 0.99)
                    ),
                    List.of(
                        new GraphExtractionResult.ExtractedRelationship(
                            "HAS_PARTY",
                            "Contract",
                            Map.of("contractId", "C-SHARED"),
                            "Party",
                            Map.of("partyId", "P-SHARED"),
                            Map.of(),
                            0.95
                        )
                    )
                );
            };
        }

        private static List<Double> vectorOf(double base) {
            List<Double> vector = new ArrayList<>(1536);
            for (int i = 0; i < 1536; i++) {
                vector.add(base + (i * 0.000001));
            }
            return vector;
        }
    }

    static final class RetryCounters implements IntegrationTestLifecycle.ResettableTestDouble {
        private final AtomicInteger defaultCallCount = new AtomicInteger();
        private final AtomicInteger multiFailCallCount = new AtomicInteger();
        private final AtomicInteger overwriteCallCount = new AtomicInteger();

        @Override
        public void reset() {
            defaultCallCount.set(0);
            multiFailCallCount.set(0);
            overwriteCallCount.set(0);
        }
    }
}
