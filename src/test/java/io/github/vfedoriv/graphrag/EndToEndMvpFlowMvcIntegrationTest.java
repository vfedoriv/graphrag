package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.query.CypherGenerationClient;
import io.github.vfedoriv.graphrag.query.GeneratedCypher;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, EndToEndMvpFlowMvcIntegrationTest.DeterministicAiTestConfig.class})
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
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
class EndToEndMvpFlowMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Neo4jClient neo4jClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void completesFlowViaRestEndpoints() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        String schemaYaml = new ClassPathResource("fixtures/schemas/contracts-v1.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        byte[] documentBytes = new ClassPathResource("fixtures/documents/contract-sample.txt")
            .getContentAsByteArray();

        String createSchemaPayload = """
            {
              "content": %s,
              "sourceType": "PREDEFINED"
            }
            """.formatted(objectMapper.writeValueAsString(schemaYaml));

        String schemaBody = mockMvc.perform(post("/api/v1/schemas")
                .contentType("application/json")
                .content(createSchemaPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("contracts"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String schemaId = objectMapper.readTree(schemaBody).path("id").asText();
        assertThat(schemaId).isNotBlank();

        mockMvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/activate", "kb-e2e-mvc", schemaId))
            .andExpect(status().isOk());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "contract-sample.txt",
            "text/plain",
            documentBytes
        );
        String uploadBody = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-e2e-mvc")
                .file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UPLOADED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String documentId = objectMapper.readTree(uploadBody).path("id").asText();
        assertThat(documentId).isNotBlank();

        mockMvc.perform(post("/api/v1/documents/{documentId}/process", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/queries/ask", "kb-e2e-mvc")
                .contentType("application/json")
                .content("""
                    { "prompt": "List contract ids" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.generatedQuery.validation.valid").value(true))
            .andExpect(jsonPath("$.execution.validation.valid").value(true))
            .andExpect(jsonPath("$.execution.rowCount").value(1))
            .andExpect(jsonPath("$.execution.rows[0].contractId").value("C-100"));
    }

    @TestConfiguration
    static class DeterministicAiTestConfig {
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
