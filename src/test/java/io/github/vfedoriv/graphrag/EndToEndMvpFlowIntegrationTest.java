package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSourceType;
import io.github.vfedoriv.graphrag.search.query.api.model.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.ai.models.EmbeddingClient;
import io.github.vfedoriv.graphrag.documents.ports.GraphExtractionClient;
import io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult;
import io.github.vfedoriv.graphrag.search.query.ports.CypherGenerationClient;
import io.github.vfedoriv.graphrag.search.query.domain.GeneratedCypher;
import io.github.vfedoriv.graphrag.search.query.application.CypherExecutionService;
import io.github.vfedoriv.graphrag.search.query.application.CypherGenerationService;
import io.github.vfedoriv.graphrag.documents.application.processing.DocumentProcessingService;
import io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService;
import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@Import({TestcontainersConfiguration.class, EndToEndMvpFlowIntegrationTest.DeterministicAiTestConfig.class})
@IntegrationTest
class EndToEndMvpFlowIntegrationTest {

    @Autowired
    private SchemaRegistryService schemaRegistryService;
    @Autowired
    private DocumentUploadService documentUploadService;
    @Autowired
    private DocumentProcessingService documentProcessingService;
    @Autowired
    private CypherGenerationService cypherGenerationService;
    @Autowired
    private CypherExecutionService cypherExecutionService;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDocumentStorage() throws Exception {
        TestDocumentStorage.clean();
    }

    @Test
    void completesSynchronousFlowFromSchemaToAsk() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);

        String schemaYaml = new ClassPathResource("fixtures/schemas/contracts-v1.json")
            .getContentAsString(StandardCharsets.UTF_8);
        byte[] documentBytes = new ClassPathResource("fixtures/documents/contract-sample.txt")
            .getContentAsByteArray();

        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaYaml, SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-e2e", schema.getId());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "contract-sample.txt",
            "text/plain",
            documentBytes
        );
        DocumentUploadNode uploaded = documentUploadService.upload("kb-e2e", file);
        assertThat(uploaded.getStatus().name()).isEqualTo("UPLOADED");

        DocumentUploadNode processed = documentProcessingService.process(uploaded.getId());
        assertThat(processed.getStatus().name()).isEqualTo("COMPLETED");

        GeneratedQueryResponse generated = cypherGenerationService.generate("kb-e2e", "List contract ids");
        assertThat(generated.validation().valid()).isTrue();
        assertThat(generated.cypher()).contains("MATCH (c:Contract)");

        QueryExecutionResponse execution = cypherExecutionService.execute(
            "kb-e2e",
            generated.validation().cypher(),
            generated.validation().parameters()
        );
        assertThat(execution.validation().valid()).isTrue();
        assertThat(execution.rowCount()).isEqualTo(1);
        assertThat(execution.rows()).containsExactly(Map.of("contractId", "C-100"));

        assertGraphPurity();
    }

    private void assertGraphPurity() {
        Set<String> labels = Set.copyOf(neo4jClient.query("""
            MATCH (node)
            UNWIND labels(node) AS label
            RETURN DISTINCT label
            """).fetchAs(String.class).all());
        Set<String> allowedLabels = Set.of(
            "DocumentChunk",
            "GraphExtractionEvidence",
            "NodeExtractionEvidence",
            "RelationshipExtractionEvidence",
            "Contract",
            "Party"
        );
        assertThat(labels)
            .contains("DocumentChunk", "GraphExtractionEvidence", "Contract", "Party")
            .allMatch(label -> allowedLabels.contains(label)
                || label.startsWith("EmbeddingSpace_")
                || label.startsWith("KnowledgeBaseText_"));

        Set<String> relationshipTypes = Set.copyOf(neo4jClient.query("""
            MATCH ()-[relationship]->()
            RETURN DISTINCT type(relationship)
            """).fetchAs(String.class).all());
        assertThat(relationshipTypes)
            .contains("HAS_CHILD", "HAS_GRAPH_EVIDENCE", "ASSERTS_NODE", "ASSERTS_FROM", "ASSERTS_TO", "HAS_PARTY")
            .allMatch(Set.of(
                "HAS_CHILD",
                "HAS_GRAPH_EVIDENCE",
                "ASSERTS_NODE",
                "ASSERTS_FROM",
                "ASSERTS_TO",
                "HAS_PARTY"
            )::contains);

        Set<String> graphSchemaObjects = Set.copyOf(neo4jClient.query("""
            SHOW INDEXES YIELD name
            WHERE name STARTS WITH 'document_chunk_'
               OR name STARTS WITH 'graph_extraction_evidence_'
            RETURN name
            """).fetchAs(String.class).all());
        assertThat(graphSchemaObjects).contains(
            "document_chunk_id",
            "document_chunk_knowledge_base",
            "document_chunk_document",
            "document_chunk_scope_space",
            "graph_extraction_evidence_id",
            "graph_extraction_evidence_knowledge_base",
            "graph_extraction_evidence_document",
            "graph_extraction_evidence_run",
            "graph_extraction_evidence_fact",
            "graph_extraction_evidence_chunk"
        );
        assertThat(graphSchemaObjects).anyMatch(name -> name.startsWith("document_chunk_embedding_"));
    }

    @TestConfiguration
    static class DeterministicAiTestConfig {
        @Bean
        EmbeddingClient embeddingClient() {
            return texts -> {
                List<List<Double>> out = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    out.add(vectorOf(0.31 + i));
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
                        Map.of("contractId", "C-100", "title", "Master Supply Agreement"),
                        0.99
                    ),
                    new GraphExtractionResult.ExtractedNode(
                        "Party",
                        Map.of("name", "Acme Corp"),
                        0.98
                    )
                ),
                List.of(
                    new GraphExtractionResult.ExtractedRelationship(
                        "HAS_PARTY",
                        "Contract",
                        Map.of("contractId", "C-100"),
                        "Party",
                        Map.of("name", "Acme Corp"),
                        Map.of("role", "Supplier"),
                        0.95
                    )
                )
            );
        }

        @Bean
        CypherGenerationClient cypherGenerationClient() {
            return (schema, prompt, maxRows) -> new GeneratedCypher(
                "MATCH (c:Contract) RETURN c.contractId AS contractId LIMIT $limit",
                "Return contract identifiers from extracted contracts.",
                Map.of("limit", maxRows)
            );
        }

        private static List<Double> vectorOf(double base) {
            List<Double> vector = new ArrayList<>(1536);
            for (int i = 0; i < 1536; i++) {
                vector.add(base + (i * 0.000001));
            }
            return vector;
        }
    }
}
