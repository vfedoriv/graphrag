package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;

class IntegrationTestLifecycleTest {

    @Test
    void reportsEveryCleanupFailure() {
        IntegrationTestLifecycle.ResettableTestDouble first = () -> {
            throw new IllegalStateException("first cleanup failed");
        };
        IntegrationTestLifecycle.ResettableTestDouble second = () -> {
            throw new IllegalArgumentException("second cleanup failed");
        };

        assertThatThrownBy(() -> IntegrationTestLifecycle.reset(
            mock(JdbcTemplate.class),
            mock(Neo4jClient.class, RETURNS_DEEP_STUBS),
            first,
            second
        ))
            .isInstanceOf(MultipleFailuresError.class)
            .hasMessageContaining("first cleanup failed")
            .hasMessageContaining("second cleanup failed");
    }
}
