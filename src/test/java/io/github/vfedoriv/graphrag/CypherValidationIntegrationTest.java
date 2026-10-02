package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.query.QueryValidationResult;
import io.github.vfedoriv.graphrag.service.CypherValidationService;
import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@IntegrationTest
class CypherValidationIntegrationTest {

    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private SchemaRegistryService schemaRegistryService;
    @Autowired
    private CypherValidationService cypherValidationService;

    @Test
    void validatesWithExplainAgainstNeo4j() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        SchemaDefinitionNode schema = schemaRegistryService.createSchema("""
            {
              "name": "contracts",
              "version": 1,
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
        schemaRegistryService.activateSchema("kb-validate", schema.getId());

        neo4jClient.query("CREATE (:Contract {contractId: 'C-1'})").run();

        QueryValidationResult valid = cypherValidationService.validate(
            "kb-validate",
            "MATCH (c:Contract) RETURN c.contractId",
            Map.of()
        );
        QueryValidationResult invalid = cypherValidationService.validate(
            "kb-validate",
            "MATCH (c:Contract RETURN c.contractId",
            Map.of()
        );

        assertThat(valid.valid()).isTrue();
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors()).anyMatch(e -> e.contains("planner validation failed"));
    }
}
