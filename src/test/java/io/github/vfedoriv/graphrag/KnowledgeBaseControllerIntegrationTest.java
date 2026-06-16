package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
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
    void createKnowledgeBaseValidationFailureReturnsProblemDetails() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        mockMvc.perform(post("/api/v1/knowledge-bases")
                .contentType("application/json")
                .content("""
                    {
                      "id": "",
                      "name": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Validation failed"))
            .andExpect(jsonPath("$.instance").value("/api/v1/knowledge-bases"))
            .andExpect(jsonPath("$.errors.id").exists())
            .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void deleteKnowledgeBaseDetachesSchemaRelation() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        String schemaBody = mockMvc.perform(post("/api/v1/schemas")
                .contentType("application/json")
                .content("""
                    {
                      "content": "{\\"name\\":\\"contracts\\",\\"version\\":1,\\"nodes\\":[{\\"label\\":\\"Contract\\",\\"key\\":\\"contractId\\",\\"properties\\":[{\\"name\\":\\"contractId\\",\\"type\\":\\"string\\"}]}],\\"relationships\\":[]}",
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

    @Test
    void createSchemaWithKnowledgeBaseAppearsInKnowledgeBaseSchemaListWithoutActivation() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        createKnowledgeBase("kb-schema-create");

        String schemaBody = mockMvc.perform(post("/api/v1/schemas")
                .contentType("application/json")
                .content(createSchemaPayload("contracts-create-kb", "kb-schema-create")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("contracts-create-kb"))
            .andExpect(jsonPath("$.status").value("INACTIVE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String schemaId = objectMapper.readTree(schemaBody).path("id").asText();

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/schemas", "kb-schema-create"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(schemaId))
            .andExpect(jsonPath("$[0].status").value("INACTIVE"));

        String knowledgeBaseBody = mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}", "kb-schema-create"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(objectMapper.readTree(knowledgeBaseBody).path("activeSchemaId").isNull()).isTrue();
    }

    @Test
    void attachExistingSchemaIsIdempotentAndDoesNotActivateSchema() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        createKnowledgeBase("kb-schema-attach");
        String schemaId = createSchemaAndReturnId("contracts-attach-kb", null);

        mockMvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/attach", "kb-schema-attach", schemaId))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/attach", "kb-schema-attach", schemaId))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/schemas", "kb-schema-attach"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(schemaId))
            .andExpect(jsonPath("$[0].status").value("INACTIVE"));

        assertThat(usesSchemaTargetCount("kb-schema-attach", schemaId)).isEqualTo(1L);
        String knowledgeBaseBody = mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}", "kb-schema-attach"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(objectMapper.readTree(knowledgeBaseBody).path("activeSchemaId").isNull()).isTrue();
    }

    @Test
    void createSchemaWithUnknownKnowledgeBaseReturnsProblemDetailWithoutPersistingSchema() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        mockMvc.perform(post("/api/v1/schemas")
                .contentType("application/json")
                .content(createSchemaPayload("contracts-missing-kb", "missing-kb")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Knowledge base not found: missing-kb"))
            .andExpect(jsonPath("$.status").value(404));

        assertThat(schemaCount()).isZero();
    }

    @Test
    void attachSchemaMissingResourcesReturnProblemDetail() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        String schemaId = createSchemaAndReturnId("contracts-attach-errors", null);

        mockMvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/attach", "missing-kb", schemaId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Knowledge base not found: missing-kb"))
            .andExpect(jsonPath("$.status").value(404));

        createKnowledgeBase("kb-attach-errors");
        mockMvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/attach", "kb-attach-errors", "missing-schema"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Schema not found: missing-schema"))
            .andExpect(jsonPath("$.status").value(404));

        assertThat(usesSchemaRelationCount("kb-attach-errors")).isZero();
    }

    @Test
    void createSchemaWithoutKnowledgeBaseRemainsGlobalOnly() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        createKnowledgeBase("kb-global-only");

        createSchemaAndReturnId("contracts-global-only", null);

        mockMvc.perform(get("/api/v1/schemas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("contracts-global-only"));
        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/schemas", "kb-global-only"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    private void createKnowledgeBase(String knowledgeBaseId) throws Exception {
        mockMvc.perform(post("/api/v1/knowledge-bases")
                .contentType("application/json")
                .content("""
                    {
                      "id": "%s",
                      "name": "%s"
                    }
                    """.formatted(knowledgeBaseId, knowledgeBaseId)))
            .andExpect(status().isOk());
    }

    private String createSchemaAndReturnId(String name, String knowledgeBaseId) throws Exception {
        String schemaBody = mockMvc.perform(post("/api/v1/schemas")
                .contentType("application/json")
                .content(createSchemaPayload(name, knowledgeBaseId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(schemaBody).path("id").asText();
    }

    private String createSchemaPayload(String name, String knowledgeBaseId) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", schemaJson(name));
        payload.put("sourceType", "GENERATED");
        if (knowledgeBaseId != null) {
            payload.put("knowledgeBaseId", knowledgeBaseId);
        }
        return objectMapper.writeValueAsString(payload);
    }

    private String schemaJson(String name) {
        return """
            {
              "name": "%s",
              "version": 1,
              "nodes": [
                {"label": "Contract", "key": "contractId", "properties": [{"name": "contractId", "type": "string"}]}
              ],
              "relationships": []
            }
            """.formatted(name);
    }

    private Long schemaCount() {
        return neo4jClient.query("MATCH (s:SchemaDefinition) RETURN count(s)")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);
    }

    private Long usesSchemaRelationCount(String knowledgeBaseId) {
        return neo4jClient.query("""
            MATCH (:KnowledgeBase {id: $kbId})-[r:USES_SCHEMA]->(:SchemaDefinition)
            RETURN count(r) AS c
            """)
            .bind(knowledgeBaseId).to("kbId")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);
    }

    private Long usesSchemaTargetCount(String knowledgeBaseId, String schemaId) {
        return neo4jClient.query("""
            MATCH (:KnowledgeBase {id: $kbId})-[:USES_SCHEMA]->(:SchemaDefinition {id: $schemaId})
            RETURN count(*) AS c
            """)
            .bind(knowledgeBaseId).to("kbId")
            .bind(schemaId).to("schemaId")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);
    }
}
