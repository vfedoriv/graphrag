package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaAiProfileRepository;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaKnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaKnowledgeBaseSchemaRepository;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaSchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.repository.AiProfileRepository;
import io.github.vfedoriv.graphrag.service.AiProfileService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
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
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration"
})
class KnowledgeBaseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private AiProfileRepository aiProfileRepository;
    @Autowired
    private JpaAiProfileRepository jpaAiProfileRepository;
    @Autowired
    private JpaKnowledgeBaseRepository jpaKnowledgeBaseRepository;
    @Autowired
    private JpaKnowledgeBaseSchemaRepository jpaKnowledgeBaseSchemaRepository;
    @Autowired
    private JpaSchemaDefinitionRepository jpaSchemaDefinitionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AiProfileService aiProfileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetOperationalState() {
        resetRelationalMetadata();
        jpaAiProfileRepository.deleteAll();
        aiProfileService.seedDefaultProfile();
    }

    @Test
    void createListGetUpdateDeleteKnowledgeBase() throws Exception {
        resetRelationalMetadata();

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
        resetRelationalMetadata();

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
    void createKnowledgeBaseSeedsDefaultProfileAndPersistsProfileAssignment() throws Exception {
        resetRelationalMetadata();

        mockMvc.perform(post("/api/v1/knowledge-bases")
                .contentType("application/json")
                .content("""
                    {
                      "id": "kb-ai-profile",
                      "name": "KB AI Profile"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("kb-ai-profile"))
            .andExpect(jsonPath("$.activeAiProfileId").value("default"));

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/ai-profile", "kb-ai-profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("default"))
            .andExpect(jsonPath("$.apiKeyConfigured").isBoolean())
            .andExpect(jsonPath("$.apiKey").doesNotExist());

        mockMvc.perform(post("/api/v1/ai-profiles")
                .contentType("application/json")
                .content("""
                    {
                      "id": "profile-alt",
                      "name": "Alternate Profile",
                      "baseUrl": "https://profiles.example/v1",
                      "apiKey": "secret-profile-key",
                      "chatModel": "chat-alt",
                      "embeddingModel": "text-embedding-3-small",
                      "embeddingDimensions": 1536,
                      "timeoutSeconds": 30,
                      "maxRetries": 1,
                      "defaultProfile": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("profile-alt"))
            .andExpect(jsonPath("$.apiKeyConfigured").value(true))
            .andExpect(jsonPath("$.apiKeyMask").value("secr...-key"))
            .andExpect(jsonPath("$.apiKey").doesNotExist());

        mockMvc.perform(put("/api/v1/knowledge-bases/{knowledgeBaseId}/ai-profile", "kb-ai-profile")
                .contentType("application/json")
                .content("""
                    {
                      "profileId": "profile-alt"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeAiProfileId").value("profile-alt"));

        String persistedProfileId = jpaKnowledgeBaseRepository.findById("kb-ai-profile")
            .orElseThrow()
            .getActiveAiProfileId();
        assertThat(persistedProfileId).isEqualTo("profile-alt");
    }

    @Test
    void updatesRelationalAiProfileWithOptimisticVersion() throws Exception {
        AiProfileNode profile = new AiProfileNode();
        profile.setId("legacy-profile");
        profile.setName("Legacy Profile");
        profile.setBaseUrl("https://profiles.example/v1");
        profile.setApiKey("legacy-secret");
        profile.setChatModel("legacy-chat");
        profile.setEmbeddingModel("legacy-embedding");
        profile.setEmbeddingDimensions(768);
        profile.setTimeoutSeconds(60);
        profile.setMaxRetries(1);
        profile.setDefaultProfile(false);
        profile.setRevision(1);
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());
        aiProfileRepository.save(profile);

        mockMvc.perform(put("/api/v1/ai-profiles/{profileId}", "legacy-profile")
                .contentType("application/json")
                .content("""
                    {
                      "name": "Legacy Profile",
                      "baseUrl": "https://profiles.example/v1",
                      "chatModel": "legacy-chat",
                      "embeddingModel": "legacy-embedding",
                      "embeddingDimensions": 768,
                      "timeoutSeconds": 600,
                      "maxRetries": 1,
                      "defaultProfile": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("legacy-profile"))
            .andExpect(jsonPath("$.timeoutSeconds").value(600))
            .andExpect(jsonPath("$.revision").value(2))
            .andExpect(jsonPath("$.apiKeyConfigured").value(true));

        io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.AiProfileEntity persisted =
            jpaAiProfileRepository.findById("legacy-profile").orElseThrow();
        assertThat(persisted.getVersion()).isGreaterThanOrEqualTo(1L);
        assertThat(persisted.getTimeoutSeconds()).isEqualTo(600);
    }

    @Test
    void createKnowledgeBaseValidationFailureReturnsProblemDetails() throws Exception {
        resetRelationalMetadata();

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
        resetRelationalMetadata();

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

        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM app.knowledge_base WHERE id = ?",
            Long.class,
            "kb-rel"
        );
        assertThat(count).isEqualTo(0L);
        assertThat(usesSchemaTargetCount("kb-rel", schemaId)).isZero();
    }

    @Test
    void createSchemaWithKnowledgeBaseAppearsInKnowledgeBaseSchemaListWithoutActivation() throws Exception {
        resetRelationalMetadata();
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
        resetRelationalMetadata();
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
        resetRelationalMetadata();

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
        resetRelationalMetadata();
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
        resetRelationalMetadata();
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
        return jpaSchemaDefinitionRepository.count();
    }

    private Long usesSchemaRelationCount(String knowledgeBaseId) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM app.knowledge_base_schema WHERE knowledge_base_id = ?",
            Long.class,
            knowledgeBaseId
        );
    }

    private Long usesSchemaTargetCount(String knowledgeBaseId, String schemaId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM app.knowledge_base_schema
            WHERE knowledge_base_id = ? AND schema_id = ?
            """,
            Long.class,
            knowledgeBaseId,
            schemaId
        );
    }

    private void resetRelationalMetadata() {
        jpaKnowledgeBaseSchemaRepository.deleteAllInBatch();
        jpaKnowledgeBaseRepository.deleteAllInBatch();
        jpaSchemaDefinitionRepository.deleteAllInBatch();
    }
}
