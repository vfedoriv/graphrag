package io.github.vfedoriv.graphrag;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;

final class IntegrationTestLifecycle {

    private IntegrationTestLifecycle() {
    }

    static void reset(
        JdbcTemplate jdbcTemplate,
        Neo4jClient neo4jClient,
        ResettableTestDouble... testDoubles
    ) throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        TestDocumentStorage.clean();
        for (ResettableTestDouble testDouble : testDoubles) {
            testDouble.reset();
        }
    }

    interface ResettableTestDouble {
        void reset();
    }
}
