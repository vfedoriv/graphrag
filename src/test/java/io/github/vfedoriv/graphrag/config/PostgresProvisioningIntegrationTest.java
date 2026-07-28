package io.github.vfedoriv.graphrag.config;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresProvisioningIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17");
    private static final String ADMIN_USER = "langfuse";
    private static final String ADMIN_PASSWORD = "langfuse-test-password";
    private static final String APP_USER = "graphrag";
    private static final String APP_PASSWORD = "graphrag-test-password";
    private static final Path PROVISIONING_SCRIPT = Path.of("docker/postgres/init-graphrag.sh").toAbsolutePath();

    @Test
    void provisionsFreshServerDuringFirstInitialization() throws Exception {
        try (PostgreSQLContainer<?> postgres = postgresContainer()
            .withCopyFileToContainer(
                MountableFile.forHostPath(PROVISIONING_SCRIPT, 0744),
                "/docker-entrypoint-initdb.d/20-init-graphrag.sh"
            )) {
            postgres.start();

            assertProvisioned(postgres);
        }
    }

    @Test
    void provisionsPopulatedServerIdempotentlyWithoutExposingLangfuseData() throws Exception {
        try (PostgreSQLContainer<?> postgres = postgresContainer()) {
            postgres.start();
            try (Connection admin = adminConnection(postgres); Statement statement = admin.createStatement()) {
                statement.execute("CREATE TABLE langfuse_marker (value text NOT NULL)");
                statement.execute("INSERT INTO langfuse_marker (value) VALUES ('preserved')");
            }

            postgres.copyFileToContainer(
                MountableFile.forHostPath(PROVISIONING_SCRIPT, 0744),
                "/tmp/init-graphrag.sh"
            );
            executeProvisioning(postgres);
            executeProvisioning(postgres);

            assertProvisioned(postgres);
            try (Connection admin = adminConnection(postgres);
                 Statement statement = admin.createStatement();
                 ResultSet result = statement.executeQuery("SELECT value FROM langfuse_marker")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("preserved");
            }

            assertThatThrownBy(() -> queryAsApplication(
                postgres,
                "langfuse",
                "SELECT value FROM langfuse_marker"
            )).isInstanceOf(SQLException.class);
        }
    }

    private PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("langfuse")
            .withUsername(ADMIN_USER)
            .withPassword(ADMIN_PASSWORD)
            .withEnv("GRAPHRAG_POSTGRES_DATABASE", "graphrag")
            .withEnv("GRAPHRAG_POSTGRES_USER", APP_USER)
            .withEnv("GRAPHRAG_POSTGRES_PASSWORD", APP_PASSWORD)
            .withEnv("GRAPHRAG_POSTGRES_SCHEMA", "app");
    }

    private void executeProvisioning(PostgreSQLContainer<?> postgres) throws Exception {
        Container.ExecResult result = postgres.execInContainer("bash", "/tmp/init-graphrag.sh");
        assertThat(result.getExitCode())
            .withFailMessage("Provisioning failed: %s%n%s", result.getStdout(), result.getStderr())
            .isZero();
    }

    private void assertProvisioned(PostgreSQLContainer<?> postgres) throws Exception {
        try (Connection app = applicationConnection(postgres, "graphrag");
             Statement statement = app.createStatement();
             ResultSet schema = statement.executeQuery(
                 "SELECT schema_owner FROM information_schema.schemata WHERE schema_name = 'app'"
             )) {
            assertThat(schema.next()).isTrue();
            assertThat(schema.getString(1)).isEqualTo(APP_USER);
        }

        try (Connection admin = adminConnection(postgres);
             Statement statement = admin.createStatement();
             ResultSet role = statement.executeQuery(
                 "SELECT rolsuper, rolcreatedb, rolcreaterole, rolreplication "
                     + "FROM pg_roles WHERE rolname = 'graphrag'"
             )) {
            assertThat(role.next()).isTrue();
            assertThat(role.getBoolean("rolsuper")).isFalse();
            assertThat(role.getBoolean("rolcreatedb")).isFalse();
            assertThat(role.getBoolean("rolcreaterole")).isFalse();
            assertThat(role.getBoolean("rolreplication")).isFalse();
        }

        assertThatThrownBy(() -> queryAsApplication(postgres, "graphrag", "CREATE ROLE forbidden"))
            .isInstanceOf(SQLException.class);
    }

    private Connection adminConnection(PostgreSQLContainer<?> postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), ADMIN_USER, ADMIN_PASSWORD);
    }

    private Connection applicationConnection(PostgreSQLContainer<?> postgres, String database) throws SQLException {
        String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + database;
        return DriverManager.getConnection(jdbcUrl, APP_USER, APP_PASSWORD);
    }

    private void queryAsApplication(PostgreSQLContainer<?> postgres, String database, String sql) throws SQLException {
        try (Connection connection = applicationConnection(postgres, database);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
