package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.error.QueryRejectedException;
import io.github.vfedoriv.graphrag.service.CypherExecutionService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import java.util.Map;
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
class CypherExecutionIntegrationTest {

    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private SchemaRegistryService schemaRegistryService;
    @Autowired
    private CypherExecutionService cypherExecutionService;

    @Test
    void executesValidatedReadOnlyQuery() {
        neo4jClient.query("MATCH (n:ExecutionTestData) DETACH DELETE n").run();
        var schema = schemaRegistryService.createSchema("""
            name: execute-contracts
            version: 101
            nodes:
              - label: Contract
                key: contractId
                properties:
                  - name: contractId
                    type: string
            relationships: []
            """, SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-execute", schema.getId());
        neo4jClient.query("CREATE (:Contract:ExecutionTestData {contractId: 'C-1'})").run();

        var response = cypherExecutionService.execute(
            "kb-execute",
            "MATCH (c:Contract) RETURN c.contractId AS contractId",
            Map.of()
        );

        assertThat(response.validation().valid()).isTrue();
        assertThat(response.rows()).containsExactly(Map.of("contractId", "C-1"));
    }

    @Test
    void rejectsInvalidQueryBeforeExecution() {
        neo4jClient.query("MATCH (n:ExecutionTestData) DETACH DELETE n").run();
        var schema = schemaRegistryService.createSchema("""
            name: reject-contracts
            version: 102
            nodes:
              - label: Contract
                key: contractId
                properties:
                  - name: contractId
                    type: string
            relationships: []
            """, SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-reject", schema.getId());
        neo4jClient.query("CREATE (:Contract:ExecutionTestData {contractId: 'C-1'})").run();

        assertThatThrownBy(() -> cypherExecutionService.execute(
            "kb-reject",
            "MATCH (c:Contract) DELETE c",
            Map.of()
        )).isInstanceOf(QueryRejectedException.class);

        var count = neo4jClient.query("MATCH (c:Contract) RETURN count(c) AS cnt").fetch().all();
        assertThat(count).containsExactly(Map.of("cnt", 1L));
    }
}
