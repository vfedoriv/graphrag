package io.github.vfedoriv.graphrag;

import java.util.ArrayList;
import java.util.List;
import org.opentest4j.MultipleFailuresError;
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
        List<Throwable> failures = new ArrayList<>();
        runCleanup(failures, () -> {
            if (neo4jClient != null) {
                neo4jClient.query("MATCH (n) DETACH DELETE n").run();
            }
        });
        runCleanup(failures, () -> RelationalMetadataTestCleaner.clean(jdbcTemplate));
        runCleanup(failures, TestDocumentStorage::clean);
        for (ResettableTestDouble testDouble : testDoubles) {
            runCleanup(failures, testDouble::reset);
        }
        if (!failures.isEmpty()) {
            throw new MultipleFailuresError("Integration test baseline restoration failed", failures);
        }
    }

    private static void runCleanup(List<Throwable> failures, CleanupAction action) {
        try {
            action.run();
        } catch (Throwable failure) {
            failures.add(failure);
        }
    }

    @FunctionalInterface
    private interface CleanupAction {
        void run() throws Exception;
    }

    interface ResettableTestDouble {
        void reset();
    }
}
