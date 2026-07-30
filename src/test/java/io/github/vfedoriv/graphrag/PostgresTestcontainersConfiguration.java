package io.github.vfedoriv.graphrag;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
class PostgresTestcontainersConfiguration {

    @Bean
    JdbcConnectionDetails jdbcConnectionDetails() {
        PostgreSQLContainer<?> container = SharedApplicationIntegrationContainers.postgres();
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return container.getUsername();
            }

            @Override
            public String getPassword() {
                return container.getPassword();
            }

            @Override
            public String getJdbcUrl() {
                return container.getJdbcUrl();
            }
        };
    }

    @Bean
    org.springframework.context.ApplicationListener<ContextRefreshedEvent> integrationContextReporter() {
        return event -> System.out.printf(
            "GRAPHRAG_TEST_CONTEXT_START id=%s%n",
            event.getApplicationContext().getId()
        );
    }
}
