package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.service.SchemaBootstrapService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration"
})
class SchemaRegistryIntegrationTest {

    @Autowired
    private SchemaRegistryService schemaRegistryService;
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SchemaBootstrapService schemaBootstrapService;

    @Test
    void persistsAndActivatesSchema() {
        resetRelationalMetadata();

        String json = """
            {
              "name": "contracts",
              "version": 1,
              "nodes": [
                {"label": "Contract", "key": "contractId", "properties": [{"name": "contractId", "type": "string"}]},
                {"label": "Party", "key": "name", "properties": [{"name": "name", "type": "string"}]}
              ],
              "relationships": [
                {"type": "HAS_PARTY", "from": "Contract", "to": "Party"}
              ]
            }
            """;

        SchemaDefinitionNode schema = schemaRegistryService.createSchema(json, SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-1", schema.getId());

        KnowledgeBaseNode kb = knowledgeBaseRepository.findById("kb-1").orElseThrow();
        assertThat(kb.getActiveSchemaId()).isEqualTo(schema.getId());

        Long relCount = usesSchemaTargetCount("kb-1", schema.getId());
        assertThat(relCount).isEqualTo(1);
    }

    @Test
    void schemaVersionIsImmutable() {
        resetRelationalMetadata();

        String json = """
            {
              "name": "contracts",
              "version": 1,
              "nodes": [
                {"label": "Contract", "key": "contractId", "properties": [{"name": "contractId", "type": "string"}]}
              ],
              "relationships": []
            }
            """;
        schemaRegistryService.createSchema(json, SchemaSourceType.PREDEFINED);

        assertThatThrownBy(() -> schemaRegistryService.createSchema(json, SchemaSourceType.GENERATED))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @Order(1)
    void bootstrapsPredefinedSchemasFromResources() throws IOException {
        resetRelationalMetadata();
        schemaBootstrapService.bootstrap();
        List<SchemaDefinitionNode> schemas = schemaRegistryService.listSchemas();
        assertThat(schemas).extracting("name").contains("legal-contracts", "cmms");
    }

    @Test
    void activatingSchemaDeactivatesSiblingsWithinKnowledgeBase() {
        resetRelationalMetadata();

        SchemaDefinitionNode first = schemaRegistryService.createSchema(schemaJson("contracts-a"), SchemaSourceType.PREDEFINED);
        SchemaDefinitionNode second = schemaRegistryService.createSchema(schemaJson("contracts-b"), SchemaSourceType.PREDEFINED);

        schemaRegistryService.activateSchema("kb-single", first.getId());
        schemaRegistryService.activateSchema("kb-single", second.getId());

        KnowledgeBaseNode kb = knowledgeBaseRepository.findById("kb-single").orElseThrow();
        SchemaDefinitionNode firstReloaded = schemaRegistryService.getSchema(first.getId());
        SchemaDefinitionNode secondReloaded = schemaRegistryService.getSchema(second.getId());

        assertThat(kb.getActiveSchemaId()).isEqualTo(second.getId());
        assertThat(firstReloaded.getStatus()).isEqualTo(SchemaStatus.INACTIVE);
        assertThat(secondReloaded.getStatus()).isEqualTo(SchemaStatus.ACTIVE);
        assertThat(usesSchemaRelationCount("kb-single")).isEqualTo(2L);
    }

    @Test
    void activatingSchemaDoesNotAffectOtherKnowledgeBases() {
        resetRelationalMetadata();

        SchemaDefinitionNode kb1SchemaA = schemaRegistryService.createSchema(schemaJson("kb1-a"), SchemaSourceType.PREDEFINED);
        SchemaDefinitionNode kb1SchemaB = schemaRegistryService.createSchema(schemaJson("kb1-b"), SchemaSourceType.PREDEFINED);
        SchemaDefinitionNode kb2Schema = schemaRegistryService.createSchema(schemaJson("kb2-a"), SchemaSourceType.PREDEFINED);

        schemaRegistryService.activateSchema("kb-one", kb1SchemaA.getId());
        schemaRegistryService.activateSchema("kb-two", kb2Schema.getId());
        schemaRegistryService.activateSchema("kb-one", kb1SchemaB.getId());

        KnowledgeBaseNode kbOne = knowledgeBaseRepository.findById("kb-one").orElseThrow();
        KnowledgeBaseNode kbTwo = knowledgeBaseRepository.findById("kb-two").orElseThrow();

        assertThat(kbOne.getActiveSchemaId()).isEqualTo(kb1SchemaB.getId());
        assertThat(kbTwo.getActiveSchemaId()).isEqualTo(kb2Schema.getId());
        assertThat(usesSchemaTargetCount("kb-two", kb2Schema.getId())).isEqualTo(1L);
    }

    @Test
    void repeatedActivationIsIdempotent() {
        resetRelationalMetadata();

        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaJson("contracts-idempotent"), SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-repeat", schema.getId());
        schemaRegistryService.activateSchema("kb-repeat", schema.getId());

        KnowledgeBaseNode kb = knowledgeBaseRepository.findById("kb-repeat").orElseThrow();
        assertThat(kb.getActiveSchemaId()).isEqualTo(schema.getId());
        assertThat(usesSchemaRelationCount("kb-repeat")).isEqualTo(1L);
        assertThat(usesSchemaTargetCount("kb-repeat", schema.getId())).isEqualTo(1L);
    }

    @Test
    void createSchemaWithKnowledgeBaseAssociatesWithoutActivating() {
        resetRelationalMetadata();
        saveKnowledgeBase("kb-create-association");

        SchemaDefinitionNode schema = schemaRegistryService.createSchema(
            schemaJson("contracts-create-association"),
            SchemaSourceType.GENERATED,
            "kb-create-association"
        );

        KnowledgeBaseNode kb = knowledgeBaseRepository.findById("kb-create-association").orElseThrow();
        SchemaDefinitionNode reloaded = schemaRegistryService.getSchema(schema.getId());

        assertThat(kb.getActiveSchemaId()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(SchemaStatus.INACTIVE);
        assertThat(usesSchemaRelationCount("kb-create-association")).isEqualTo(1L);
        assertThat(usesSchemaTargetCount("kb-create-association", schema.getId())).isEqualTo(1L);
        assertThat(schemaRegistryService.listSchemasByKnowledgeBase("kb-create-association"))
            .extracting(SchemaDefinitionNode::getId)
            .containsExactly(schema.getId());
    }

    @Test
    void attachSchemaAssociatesExistingSchemaIdempotentlyWithoutActivating() {
        resetRelationalMetadata();
        saveKnowledgeBase("kb-attach");
        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaJson("contracts-attach"), SchemaSourceType.PREDEFINED);

        schemaRegistryService.attachSchema("kb-attach", schema.getId());
        schemaRegistryService.attachSchema("kb-attach", schema.getId());

        KnowledgeBaseNode kb = knowledgeBaseRepository.findById("kb-attach").orElseThrow();
        SchemaDefinitionNode reloaded = schemaRegistryService.getSchema(schema.getId());

        assertThat(kb.getActiveSchemaId()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(SchemaStatus.INACTIVE);
        assertThat(usesSchemaRelationCount("kb-attach")).isEqualTo(1L);
        assertThat(usesSchemaTargetCount("kb-attach", schema.getId())).isEqualTo(1L);
        assertThat(schemaRegistryService.listSchemasByKnowledgeBase("kb-attach"))
            .extracting(SchemaDefinitionNode::getId)
            .containsExactly(schema.getId());
    }

    @Test
    void createSchemaWithoutKnowledgeBaseRemainsGlobal() {
        resetRelationalMetadata();
        saveKnowledgeBase("kb-global-regression");

        schemaRegistryService.createSchema(schemaJson("contracts-global-regression"), SchemaSourceType.GENERATED);

        assertThat(schemaRegistryService.listSchemas()).extracting(SchemaDefinitionNode::getName).contains("contracts-global-regression");
        assertThat(schemaRegistryService.listSchemasByKnowledgeBase("kb-global-regression")).isEmpty();
        assertThat(usesSchemaRelationCount("kb-global-regression")).isZero();
    }

    @Test
    void updatesInactiveSchemaContentAndHash() {
        resetRelationalMetadata();

        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaJson("contracts-update"), SchemaSourceType.PREDEFINED);
        String originalHash = schema.getContentHash();
        String updatedJson = schemaJson("contracts-update", "Agreement");

        SchemaDefinitionNode updated = schemaRegistryService.updateSchema(schema.getId(), updatedJson, SchemaSourceType.GENERATED);
        SchemaDefinitionNode reloaded = schemaRegistryService.getSchema(schema.getId());

        assertThat(updated.getId()).isEqualTo(schema.getId());
        assertThat(updated.getContent()).isEqualTo(updatedJson);
        assertThat(updated.getContentHash()).isNotEqualTo(originalHash);
        assertThat(updated.getSourceType()).isEqualTo(SchemaSourceType.GENERATED);
        assertThat(reloaded.getContent()).isEqualTo(updatedJson);
        assertThat(reloaded.getContentHash()).isEqualTo(updated.getContentHash());
    }

    @Test
    void deletesInactiveSchemaAndRejectsSubsequentRetrieval() {
        resetRelationalMetadata();

        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaJson("contracts-delete"), SchemaSourceType.PREDEFINED);

        schemaRegistryService.deleteSchema(schema.getId());

        assertThatThrownBy(() -> schemaRegistryService.getSchema(schema.getId()))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Schema not found: " + schema.getId());
    }

    @Test
    void updatesAndDeletesInactiveAssociatedSchemaWhileDetachingRelationship() {
        resetRelationalMetadata();

        SchemaDefinitionNode inactive = schemaRegistryService.createSchema(schemaJson("contracts-inactive"), SchemaSourceType.PREDEFINED);
        SchemaDefinitionNode active = schemaRegistryService.createSchema(schemaJson("contracts-active"), SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-associated", inactive.getId());
        schemaRegistryService.activateSchema("kb-associated", active.getId());

        String updatedInactiveJson = schemaJson("contracts-inactive", "Agreement");
        SchemaDefinitionNode updatedInactive = schemaRegistryService.updateSchema(
            inactive.getId(),
            updatedInactiveJson,
            SchemaSourceType.GENERATED
        );
        assertThat(updatedInactive.getContent()).isEqualTo(updatedInactiveJson);
        assertThat(usesSchemaTargetCount("kb-associated", inactive.getId())).isEqualTo(1L);

        schemaRegistryService.deleteSchema(inactive.getId());

        KnowledgeBaseNode kb = knowledgeBaseRepository.findById("kb-associated").orElseThrow();
        assertThat(kb.getActiveSchemaId()).isEqualTo(active.getId());
        assertThat(usesSchemaTargetCount("kb-associated", inactive.getId())).isZero();
        assertThat(usesSchemaTargetCount("kb-associated", active.getId())).isEqualTo(1L);
    }

    @Test
    void rejectsUpdateAndDeleteOfActiveSchema() {
        resetRelationalMetadata();

        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaJson("contracts-guarded"), SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-guarded", schema.getId());

        assertThatThrownBy(() -> schemaRegistryService.updateSchema(
            schema.getId(),
            schemaJson("contracts-guarded", "Agreement"),
            SchemaSourceType.GENERATED
        ))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Cannot update active schema: " + schema.getId());

        assertThatThrownBy(() -> schemaRegistryService.deleteSchema(schema.getId()))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Cannot delete active schema: " + schema.getId());

        KnowledgeBaseNode kb = knowledgeBaseRepository.findById("kb-guarded").orElseThrow();
        assertThat(kb.getActiveSchemaId()).isEqualTo(schema.getId());
        assertThat(schemaRegistryService.getSchema(schema.getId()).getContent()).isEqualTo(schema.getContent());
    }

    private String schemaJson(String name) {
        return schemaJson(name, "Contract");
    }

    private String schemaJson(String name, String label) {
        return """
            {
              "name": "%s",
              "version": 1,
              "nodes": [
                {"label": "%s", "key": "contractId", "properties": [{"name": "contractId", "type": "string"}]}
              ],
              "relationships": []
            }
            """.formatted(name, label);
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

    private void saveKnowledgeBase(String knowledgeBaseId) {
        KnowledgeBaseNode knowledgeBase = new KnowledgeBaseNode();
        knowledgeBase.setId(knowledgeBaseId);
        knowledgeBase.setName(knowledgeBaseId);
        knowledgeBase.setActiveAiProfileId("default");
        knowledgeBase.setCreatedAt(Instant.now());
        knowledgeBaseRepository.save(knowledgeBase);
    }

    private void resetRelationalMetadata() {
        jdbcTemplate.update("DELETE FROM app.knowledge_base_schema");
        jdbcTemplate.update("DELETE FROM app.knowledge_base");
        jdbcTemplate.update("DELETE FROM app.schema_definition");
    }
}
