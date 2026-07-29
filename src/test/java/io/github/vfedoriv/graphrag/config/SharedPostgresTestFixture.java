package io.github.vfedoriv.graphrag.config;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

final class SharedPostgresTestFixture implements AutoCloseable {

    static final String LANGFUSE_DATABASE = "langfuse";
    static final String LANGFUSE_USER = "langfuse";
    static final String LANGFUSE_PASSWORD = "langfuse-test-password";
    static final String GRAPHRAG_DATABASE = "graphrag";
    static final String GRAPHRAG_USER = "graphrag";
    static final String GRAPHRAG_PASSWORD = "graphrag-test-password";
    static final String GRAPHRAG_SCHEMA = "app";

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17");
    private static final Path PROVISIONING_SCRIPT =
        Path.of("docker/postgres/init-graphrag.sh").toAbsolutePath();

    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
        .withDatabaseName(LANGFUSE_DATABASE)
        .withUsername(LANGFUSE_USER)
        .withPassword(LANGFUSE_PASSWORD)
        .withEnv("GRAPHRAG_POSTGRES_DATABASE", GRAPHRAG_DATABASE)
        .withEnv("GRAPHRAG_POSTGRES_USER", GRAPHRAG_USER)
        .withEnv("GRAPHRAG_POSTGRES_PASSWORD", GRAPHRAG_PASSWORD)
        .withEnv("GRAPHRAG_POSTGRES_SCHEMA", GRAPHRAG_SCHEMA);

    void startWithProvisioningOnInit() {
        postgres.withCopyFileToContainer(
            MountableFile.forHostPath(PROVISIONING_SCRIPT, 0744),
            "/docker-entrypoint-initdb.d/20-init-graphrag.sh"
        );
        postgres.start();
    }

    void start() {
        postgres.start();
    }

    void provisionGraphRag() throws Exception {
        postgres.copyFileToContainer(
            MountableFile.forHostPath(PROVISIONING_SCRIPT, 0744),
            "/tmp/init-graphrag.sh"
        );
        assertSuccessful(postgres.execInContainer("bash", "/tmp/init-graphrag.sh"), "provision");
    }

    void resetGraphRagDatabase() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + GRAPHRAG_DATABASE + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + GRAPHRAG_DATABASE + " OWNER " + GRAPHRAG_USER);
        }
    }

    void backupGraphRag(String containerPath) throws Exception {
        assertSuccessful(postgres.execInContainer(
            "pg_dump",
            "--username=" + GRAPHRAG_USER,
            "--dbname=" + GRAPHRAG_DATABASE,
            "--format=custom",
            "--file=" + containerPath
        ), "backup");
    }

    void restoreGraphRag(String containerPath) throws Exception {
        assertSuccessful(postgres.execInContainer(
            "pg_restore",
            "--username=" + GRAPHRAG_USER,
            "--dbname=" + GRAPHRAG_DATABASE,
            "--clean",
            "--if-exists",
            "--no-owner",
            "--no-privileges",
            containerPath
        ), "restore");
    }

    Connection adminConnection() throws SQLException {
        return connection(LANGFUSE_DATABASE, LANGFUSE_USER, LANGFUSE_PASSWORD);
    }

    Connection graphRagConnection() throws SQLException {
        return connection(GRAPHRAG_DATABASE, GRAPHRAG_USER, GRAPHRAG_PASSWORD);
    }

    Connection graphRagConnection(String database) throws SQLException {
        return connection(database, GRAPHRAG_USER, GRAPHRAG_PASSWORD);
    }

    String jdbcUrl(String database) {
        return "jdbc:postgresql://" + postgres.getHost() + ":"
            + postgres.getMappedPort(5432) + "/" + database;
    }

    private Connection connection(String database, String user, String password) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), user, password);
    }

    private void assertSuccessful(Container.ExecResult result, String operation) {
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(
                operation + " failed: " + result.getStdout() + System.lineSeparator() + result.getStderr()
            );
        }
    }

    @Override
    public void close() {
        postgres.close();
    }
}
