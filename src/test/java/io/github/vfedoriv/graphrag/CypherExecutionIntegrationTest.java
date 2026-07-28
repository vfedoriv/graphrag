package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.error.QueryDeadlineExceededException;
import io.github.vfedoriv.graphrag.error.QueryRejectedException;
import io.github.vfedoriv.graphrag.dto.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.service.CypherExecutionService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;
import java.util.Collection;
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
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration",
    "app.query.timeout-seconds=1"
})
class CypherExecutionIntegrationTest {

    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private SchemaRegistryService schemaRegistryService;
    @Autowired
    private CypherExecutionService cypherExecutionService;

    @Test
    void executesValidatedQuery() {
        neo4jClient.query("MATCH (n:ExecutionTestData) DETACH DELETE n").run();
        SchemaDefinitionNode schema = schemaRegistryService.createSchema("""
            {
              "name": "execute-contracts",
              "version": 101,
              "nodes": [
                {
                  "label": "Contract",
                  "key": "contractId",
                  "properties": [{"name": "contractId", "type": "string"}]
                }
              ],
              "relationships": []
            }
            """, SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-execute", schema.getId());
        neo4jClient.query("CREATE (:Contract:ExecutionTestData {contractId: 'C-1'})").run();

        QueryExecutionResponse response = cypherExecutionService.execute(
            "kb-execute",
            "MATCH (c:Contract) RETURN c.contractId AS contractId",
            Map.of()
        );

        assertThat(response.validation().valid()).isTrue();
        assertThat(response.rows()).containsExactly(Map.of("contractId", "C-1"));
    }

    @Test
    void rejectsOversizedLiteralLimitBeforeExecution() {
        activateContractSchema("kb-oversized-literal", "oversized-literal-contracts", 104);

        assertThatThrownBy(() -> cypherExecutionService.execute(
            "kb-oversized-literal",
            "MATCH (c:Contract) RETURN c.contractId AS contractId LIMIT 201",
            Map.of()
        ))
            .isInstanceOf(QueryRejectedException.class)
            .satisfies(exception -> assertThat(((QueryRejectedException) exception).getErrors())
                .containsExactly("LIMIT exceeds configured maximum rows: 200"));
    }

    @Test
    void rejectsOversizedBoundLimitBeforeExecution() {
        activateContractSchema("kb-oversized-bound", "oversized-bound-contracts", 105);

        assertThatThrownBy(() -> cypherExecutionService.execute(
            "kb-oversized-bound",
            "MATCH (c:Contract) RETURN c.contractId AS contractId LIMIT $requested",
            Map.of("requested", 201)
        ))
            .isInstanceOf(QueryRejectedException.class)
            .satisfies(exception -> assertThat(((QueryRejectedException) exception).getErrors())
                .containsExactly("LIMIT exceeds configured maximum rows: 200"));
    }

    @Test
    void terminatesQueryThatExceedsRuntimeDeadline() {
        activateContractSchema("kb-deadline", "deadline-contracts", 106);

        assertThatThrownBy(() -> cypherExecutionService.execute(
            "kb-deadline",
            """
                UNWIND range(1, 250) AS seed
                RETURN reduce(total = seed, value IN range(1, 50000000) |
                    total + sin(toFloat(value)) + cos(toFloat(value))) AS output
                LIMIT 1
                """,
            Map.of()
        )).isInstanceOf(QueryDeadlineExceededException.class);
    }

    @Test
    void returnsNodeDataAsSerializableMapWhenReturningNodeVariable() {
        neo4jClient.query("MATCH (n:ExecutionTestData) DETACH DELETE n").run();
        SchemaDefinitionNode schema = schemaRegistryService.createSchema("""
            {
              "name": "execute-contracts-node",
              "version": 103,
              "nodes": [
                {
                  "label": "Contract",
                  "key": "contractId",
                  "properties": [
                    {"name": "contractId", "type": "string"},
                    {"name": "title", "type": "string"}
                  ]
                }
              ],
              "relationships": []
            }
            """, SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-execute-node", schema.getId());
        neo4jClient.query("CREATE (:Contract:ExecutionTestData {contractId: 'C-2', title: 'Node Payload'})").run();

        QueryExecutionResponse response = cypherExecutionService.execute(
            "kb-execute-node",
            "MATCH (c:Contract) RETURN c",
            Map.of()
        );

        assertThat(response.validation().valid()).isTrue();
        assertThat(response.rows()).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> nodeValue = (Map<String, Object>) response.rows().getFirst().get("c");
        assertThat(nodeValue.get("_type")).isEqualTo("node");
        assertThat(nodeValue).containsKeys("id", "elementId", "labels", "properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) nodeValue.get("properties");
        assertThat(properties).containsEntry("contractId", "C-2");
        assertThat(properties).containsEntry("title", "Node Payload");
    }

    @Test
    void rejectsInvalidQueryBeforeExecution() {
        neo4jClient.query("MATCH (n:ExecutionTestData) DETACH DELETE n").run();
        SchemaDefinitionNode schema = schemaRegistryService.createSchema("""
            {
              "name": "reject-contracts",
              "version": 102,
              "nodes": [
                {
                  "label": "Contract",
                  "key": "contractId",
                  "properties": [{"name": "contractId", "type": "string"}]
                }
              ],
              "relationships": []
            }
            """, SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema("kb-reject", schema.getId());
        neo4jClient.query("CREATE (:Contract:ExecutionTestData {contractId: 'C-1'})").run();

        assertThatThrownBy(() -> cypherExecutionService.execute(
            "kb-reject",
            "MATCH (c:Contract) DELETE c",
            Map.of()
        )).isInstanceOf(QueryRejectedException.class);

        Collection<Map<String, Object>> count = neo4jClient.query("MATCH (c:Contract) RETURN count(c) AS cnt").fetch().all();
        assertThat(count).containsExactly(Map.of("cnt", 1L));
    }

    private void activateContractSchema(String knowledgeBaseId, String schemaName, int version) {
        SchemaDefinitionNode schema = schemaRegistryService.createSchema("""
            {
              "name": "%s",
              "version": %d,
              "nodes": [
                {
                  "label": "Contract",
                  "key": "contractId",
                  "properties": [{"name": "contractId", "type": "string"}]
                }
              ],
              "relationships": []
            }
            """.formatted(schemaName, version), SchemaSourceType.PREDEFINED);
        schemaRegistryService.activateSchema(knowledgeBaseId, schema.getId());
    }
}
