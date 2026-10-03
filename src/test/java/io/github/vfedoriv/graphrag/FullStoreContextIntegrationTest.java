package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.bootstrap.GraphSchemaInitializer;
import java.nio.file.Files;
import java.util.Collection;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.neo4j.autoconfigure.Neo4jConnectionDetails;
import org.springframework.context.ApplicationContext;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@FullStoreIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullStoreContextIntegrationTest {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Neo4jClient neo4jClient;

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void leavesMutatingStateBeforeContextTransition() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO app.runtime_setting_override
                (setting_key, setting_value, lifecycle_state, created_at, updated_at, version)
            VALUES ('app.query.max-rows', '7', 'active', now(), now(), 0)
            """);
        neo4jClient.query("CREATE (:ContextIsolationSentinel {id: 'dirty'})").run();
        Files.createDirectories(TestDocumentStorage.ROOT);
        Files.writeString(TestDocumentStorage.ROOT.resolve("context-sentinel.txt"), "dirty");
    }

    @Test
    @Order(2)
    void startsNextContextOnlyAfterSharedStateWasRestored() {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM app.runtime_setting_override",
            Integer.class
        )).isZero();
        assertThat(neo4jClient.query(
            "MATCH (n:ContextIsolationSentinel) RETURN count(n) AS count"
        ).fetchAs(Long.class).mappedBy((typeSystem, record) -> record.get("count").asLong()).one())
            .hasValue(0L);
        assertThat(TestDocumentStorage.ROOT).doesNotExist();
        assertThat(applicationContext.getBean(Neo4jConnectionDetails.class)).isNotNull();
        assertThat(applicationContext.getBean(GraphSchemaInitializer.class)).isNotNull();

        Collection<String> constraintNames = neo4jClient.query("""
            SHOW CONSTRAINTS YIELD name
            RETURN name
            """).fetchAs(String.class)
            .mappedBy((typeSystem, record) -> record.get("name").asString())
            .all();
        assertThat(constraintNames).contains(
            "document_chunk_id",
            "graph_extraction_evidence_id"
        );
    }
}
