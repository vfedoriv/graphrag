package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@IntegrationTest
class SharedApplicationIntegrationContainersIntegrationTest {

    @Test
    void closedContextsDoNotStopOrReplaceSharedContainers() {
        String firstPostgres;
        String firstNeo4j;
        try (AnnotationConfigApplicationContext first = context()) {
            firstPostgres = SharedApplicationIntegrationContainers.postgresIdentity();
            firstNeo4j = SharedApplicationIntegrationContainers.neo4jIdentity();
        }

        try (AnnotationConfigApplicationContext second = context()) {
            assertThat(SharedApplicationIntegrationContainers.postgresIdentity()).isEqualTo(firstPostgres);
            assertThat(SharedApplicationIntegrationContainers.neo4jIdentity()).isEqualTo(firstNeo4j);
        }
    }

    private AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TestcontainersConfiguration.class);
        context.refresh();
        return context;
    }
}
