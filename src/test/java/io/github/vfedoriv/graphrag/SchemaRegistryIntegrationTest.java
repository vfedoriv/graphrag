package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import java.util.List;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;

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
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration,"
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
class SchemaRegistryIntegrationTest {

    @Autowired
    private SchemaRegistryService schemaRegistryService;
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired
    private Neo4jClient neo4jClient;

    @Test
    void persistsAndActivatesSchema() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

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

        Long relCount = neo4jClient.query("""
            MATCH (:KnowledgeBase {id: $kbId})-[:USES_SCHEMA]->(:SchemaDefinition {id: $schemaId})
            RETURN count(*) AS c
            """)
            .bind("kb-1").to("kbId")
            .bind(schema.getId()).to("schemaId")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);
        assertThat(relCount).isEqualTo(1);
    }

    @Test
    void schemaVersionIsImmutable() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

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
    void bootstrapsPredefinedSchemasFromResources() {
        List<SchemaDefinitionNode> schemas = schemaRegistryService.listSchemas();
        assertThat(schemas).extracting("name").contains("legal-contracts", "cmms");
    }

    @Test
    void activatingSchemaDeactivatesSiblingsWithinKnowledgeBase() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

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
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

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
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        SchemaDefinitionNode schema = schemaRegistryService.createSchema(schemaJson("contracts-idempotent"), SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-repeat", schema.getId());
        schemaRegistryService.activateSchema("kb-repeat", schema.getId());

        KnowledgeBaseNode kb = knowledgeBaseRepository.findById("kb-repeat").orElseThrow();
        assertThat(kb.getActiveSchemaId()).isEqualTo(schema.getId());
        assertThat(usesSchemaRelationCount("kb-repeat")).isEqualTo(1L);
        assertThat(usesSchemaTargetCount("kb-repeat", schema.getId())).isEqualTo(1L);
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
