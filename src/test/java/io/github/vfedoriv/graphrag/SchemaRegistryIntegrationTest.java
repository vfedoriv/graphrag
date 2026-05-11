package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
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

        String yaml = """
            name: contracts
            version: 1
            nodes:
              - label: Contract
                key: contractId
              - label: Party
                key: name
            relationships:
              - type: HAS_PARTY
                from: Contract
                to: Party
            """;

        SchemaDefinitionNode schema = schemaRegistryService.createSchema(yaml, SchemaSourceType.PREDEFINED);
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

        String yaml = """
            name: contracts
            version: 1
            nodes:
              - label: Contract
                key: contractId
            relationships: []
            """;
        schemaRegistryService.createSchema(yaml, SchemaSourceType.PREDEFINED);

        assertThatThrownBy(() -> schemaRegistryService.createSchema(yaml, SchemaSourceType.GENERATED))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void bootstrapsPredefinedSchemasFromResources() {
        List<SchemaDefinitionNode> schemas = schemaRegistryService.listSchemas();
        assertThat(schemas).extracting("name").contains("legal-contracts", "cmms");
    }
}
