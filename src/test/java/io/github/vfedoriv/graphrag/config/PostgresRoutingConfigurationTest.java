package io.github.vfedoriv.graphrag.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class PostgresRoutingConfigurationTest {

    @Test
    void composeDiscoveryCannotOverrideAlignedGraphRagDatasourceDefaults() throws IOException {
        String compose = Files.readString(Path.of("compose.yaml"));
        String provisioning = Files.readString(Path.of("docker/postgres/init-graphrag.sh"));
        Properties application = loadProperties("src/main/resources/application.properties");

        assertThat(compose)
            .contains("langfuse-postgres:")
            .contains("org.springframework.boot.ignore: true")
            .contains("GRAPHRAG_POSTGRES_DATABASE: '${GRAPHRAG_POSTGRES_DATABASE:-graphrag}'")
            .contains("GRAPHRAG_POSTGRES_USER: '${GRAPHRAG_POSTGRES_USER:-graphrag}'")
            .contains("GRAPHRAG_POSTGRES_SCHEMA: '${GRAPHRAG_POSTGRES_SCHEMA:-app}'");

        assertThat(application)
            .containsEntry("spring.datasource.url", "${GRAPHRAG_POSTGRES_URL:jdbc:postgresql://localhost:5433/graphrag}")
            .containsEntry("spring.datasource.username", "${GRAPHRAG_POSTGRES_USER:graphrag}")
            .containsEntry("spring.jpa.properties.hibernate.default_schema", "app")
            .containsEntry("spring.flyway.default-schema", "app")
            .containsEntry("spring.flyway.schemas", "app");

        assertThat(provisioning)
            .contains("graphrag_database=\"${GRAPHRAG_POSTGRES_DATABASE:-graphrag}\"")
            .contains("graphrag_user=\"${GRAPHRAG_POSTGRES_USER:-graphrag}\"")
            .contains("graphrag_schema=\"${GRAPHRAG_POSTGRES_SCHEMA:-app}\"");
    }

    private Properties loadProperties(String path) throws IOException {
        Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(Path.of(path))) {
            properties.load(reader);
        }
        return properties;
    }
}
