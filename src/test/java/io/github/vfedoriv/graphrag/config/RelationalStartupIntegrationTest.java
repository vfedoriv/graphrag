package io.github.vfedoriv.graphrag.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RelationalStartupIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:17")
    )
        .withDatabaseName("postgres")
        .withUsername("postgres")
        .withPassword("postgres");

    @Test
    void startsAgainstEmptyFlywayManagedSchema() throws Exception {
        String database = createDatabase("managed");

        try (ConfigurableApplicationContext context = start(database);
             Connection connection = connect(database);
             Statement statement = connection.createStatement()) {
            assertThat(context.isActive()).isTrue();
            assertThat(statement.executeQuery(
                "SELECT count(*) FROM app.flyway_schema_history"
            ).next()).isTrue();
        }
    }

    @Test
    void rejectsNonEmptyUnmanagedSchemaInsteadOfBaselining() throws Exception {
        String database = createDatabase("unmanaged");
        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA app");
            statement.execute("CREATE TABLE app.unmanaged_state (id integer PRIMARY KEY)");
        }

        assertThatThrownBy(() -> start(database))
            .hasRootCauseMessage(
                "Found non-empty schema(s) \"app\" but no schema history table. "
                    + "Use baseline() or set baselineOnMigrate to true to initialize the schema history table."
            );
    }

    private ConfigurableApplicationContext start(String database) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("relational-startup-test", properties(database)));
        return new SpringApplicationBuilder(RelationalStartupConfiguration.class)
            .web(WebApplicationType.NONE)
            .environment(environment)
            .run();
    }

    private Map<String, Object> properties(String database) {
        return Map.ofEntries(
            Map.entry("spring.main.banner-mode", "off"),
            Map.entry("logging.level.root", "OFF"),
            Map.entry("spring.datasource.url", jdbcUrl(database)),
            Map.entry("spring.datasource.username", POSTGRES.getUsername()),
            Map.entry("spring.datasource.password", POSTGRES.getPassword()),
            Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
            Map.entry("spring.jpa.open-in-view", "false"),
            Map.entry("spring.jpa.properties.hibernate.default_schema", "app"),
            Map.entry("spring.flyway.default-schema", "app"),
            Map.entry("spring.flyway.schemas", "app"),
            Map.entry("spring.flyway.create-schemas", "true"),
            Map.entry("spring.flyway.baseline-on-migrate", "false")
        );
    }

    private String createDatabase(String prefix) throws Exception {
        String database = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        return database;
    }

    private Connection connect(String database) throws Exception {
        return DriverManager.getConnection(jdbcUrl(database), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private String jdbcUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class RelationalStartupConfiguration {
    }
}
