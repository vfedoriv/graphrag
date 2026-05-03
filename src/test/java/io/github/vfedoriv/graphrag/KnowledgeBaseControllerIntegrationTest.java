package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
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
class KnowledgeBaseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Neo4jClient neo4jClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createListGetUpdateDeleteKnowledgeBase() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        String createdBody = mockMvc.perform(post("/api/v1/knowledge-bases")
                .contentType("application/json")
                .content("""
                    {
                      "id": "kb-crud",
                      "name": "Knowledge Base CRUD"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("kb-crud"))
            .andExpect(jsonPath("$.name").value("Knowledge Base CRUD"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(objectMapper.readTree(createdBody).path("createdAt").asText()).isNotBlank();

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}", "kb-crud"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("kb-crud"))
            .andExpect(jsonPath("$.name").value("Knowledge Base CRUD"));

        mockMvc.perform(get("/api/v1/knowledge-bases"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("kb-crud"));

        mockMvc.perform(put("/api/v1/knowledge-bases/{knowledgeBaseId}", "kb-crud")
                .contentType("application/json")
                .content("""
                    {
                      "name": "Knowledge Base Renamed"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("kb-crud"))
            .andExpect(jsonPath("$.name").value("Knowledge Base Renamed"));

        mockMvc.perform(delete("/api/v1/knowledge-bases/{knowledgeBaseId}", "kb-crud"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}", "kb-crud"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createDuplicateKnowledgeBaseReturnsConflict() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        String payload = """
            {
              "id": "kb-dup",
              "name": "KB Duplicate"
            }
            """;

        mockMvc.perform(post("/api/v1/knowledge-bases")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/knowledge-bases")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isConflict());
    }

    @Test
    void deleteKnowledgeBaseDetachesSchemaRelation() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        String schemaBody = mockMvc.perform(post("/api/v1/schemas")
                .contentType("application/json")
                .content("""
                    {
                      "content": "name: contracts\\nversion: 1\\nnodes:\\n  - label: Contract\\n    key: contractId\\nrelationships: []",
                      "sourceType": "PREDEFINED"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String schemaId = objectMapper.readTree(schemaBody).path("id").asText();
        assertThat(schemaId).isNotBlank();

        mockMvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/activate", "kb-rel", schemaId))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/knowledge-bases/{knowledgeBaseId}", "kb-rel"))
            .andExpect(status().isOk());

        Long count = neo4jClient.query("MATCH (kb:KnowledgeBase {id: $id}) RETURN count(kb)")
            .bind("kb-rel").to("id")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);
        assertThat(count).isEqualTo(0L);
    }
}
