package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.query.CypherGenerationClient;
import io.github.vfedoriv.graphrag.query.GeneratedCypher;
import io.github.vfedoriv.graphrag.service.CypherExecutionService;
import io.github.vfedoriv.graphrag.service.CypherGenerationService;
import io.github.vfedoriv.graphrag.service.DocumentProcessingService;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({TestcontainersConfiguration.class, EndToEndMvpFlowIntegrationTest.DeterministicAiTestConfig.class})
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration,"
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "app.storage.documents-root=./target/test-documents"
})
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

    @AfterEach
    void cleanDocumentStorage() throws Exception {
        TestDocumentStorage.clean();
    }

    @Test
    void completesSynchronousFlowFromSchemaToAsk() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        String schemaYaml = new ClassPathResource("fixtures/schemas/contracts-v1.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        byte[] documentBytes = new ClassPathResource("fixtures/documents/contract-sample.txt")
            .getContentAsByteArray();

        var schema = schemaRegistryService.createSchema(schemaYaml, SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-e2e", schema.getId());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "contract-sample.txt",
            "text/plain",
            documentBytes
        );
        var uploaded = documentUploadService.upload("kb-e2e", file);
        assertThat(uploaded.getStatus().name()).isEqualTo("UPLOADED");

        var processed = documentProcessingService.process(uploaded.getId());
        assertThat(processed.getStatus().name()).isEqualTo("COMPLETED");

        var generated = cypherGenerationService.generate("kb-e2e", "List contract ids");
        assertThat(generated.validation().valid()).isTrue();
        assertThat(generated.cypher()).contains("MATCH (c:Contract)");

        var execution = cypherExecutionService.execute(
            "kb-e2e",
            generated.validation().cypher(),
            generated.validation().parameters()
        );
        assertThat(execution.validation().valid()).isTrue();
        assertThat(execution.rowCount()).isEqualTo(1);
        assertThat(execution.rows()).containsExactly(Map.of("contractId", "C-100"));
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
