package io.github.vfedoriv.graphrag.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

class PostgresProvisioningIntegrationTest {

    @Test
    void provisionsFreshServerDuringFirstInitialization() throws Exception {
        try (SharedPostgresTestFixture fixture = new SharedPostgresTestFixture()) {
            fixture.startWithProvisioningOnInit();

            assertProvisioned(fixture);
        }
    }

    @Test
    void provisionsPopulatedServerIdempotentlyWithoutExposingLangfuseData() throws Exception {
        try (SharedPostgresTestFixture fixture = new SharedPostgresTestFixture()) {
            fixture.start();
            createLangfuseSentinel(fixture);

            fixture.provisionGraphRag();
            fixture.provisionGraphRag();

            assertProvisioned(fixture);
            assertLangfuseSentinel(fixture);
            assertThatThrownBy(() -> queryAsGraphRag(
                fixture,
                SharedPostgresTestFixture.LANGFUSE_DATABASE,
                "SELECT value FROM langfuse_marker"
            )).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void flywayResetBackupAndRestoreStayInsideGraphRagDatabase() throws Exception {
        try (SharedPostgresTestFixture fixture = new SharedPostgresTestFixture()) {
            fixture.start();
            createLangfuseSentinel(fixture);
            fixture.provisionGraphRag();

            Flyway flyway = Flyway.configure()
                .dataSource(
                    fixture.jdbcUrl(SharedPostgresTestFixture.GRAPHRAG_DATABASE),
                    SharedPostgresTestFixture.GRAPHRAG_USER,
                    SharedPostgresTestFixture.GRAPHRAG_PASSWORD
                )
                .defaultSchema(SharedPostgresTestFixture.GRAPHRAG_SCHEMA)
                .schemas(SharedPostgresTestFixture.GRAPHRAG_SCHEMA)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();
            MigrateResult migrateResult = flyway.migrate();

            assertThat(migrateResult.migrationsExecuted).isPositive();
            try (Connection application = fixture.graphRagConnection();
                 Statement statement = application.createStatement()) {
                assertSingleValue(statement, "SELECT current_user", SharedPostgresTestFixture.GRAPHRAG_USER);
                assertSingleValue(statement, """
                    SELECT table_schema
                    FROM information_schema.tables
                    WHERE table_name = 'flyway_schema_history'
                    """, SharedPostgresTestFixture.GRAPHRAG_SCHEMA);
                statement.execute("CREATE TABLE app.cutover_marker (value text NOT NULL)");
                statement.execute("INSERT INTO app.cutover_marker (value) VALUES ('restored')");
            }

            String backupPath = "/tmp/graphrag-cutover.dump";
            fixture.backupGraphRag(backupPath);
            assertLangfuseSentinel(fixture);

            fixture.resetGraphRagDatabase();
            fixture.provisionGraphRag();
            assertThatThrownBy(() -> queryAsGraphRag(
                fixture,
                SharedPostgresTestFixture.GRAPHRAG_DATABASE,
                "SELECT value FROM app.cutover_marker"
            )).isInstanceOf(SQLException.class);
            assertLangfuseSentinel(fixture);

            fixture.restoreGraphRag(backupPath);

            try (Connection application = fixture.graphRagConnection();
                 Statement statement = application.createStatement()) {
                assertSingleValue(statement, "SELECT value FROM app.cutover_marker", "restored");
                assertSingleValue(statement, "SELECT current_user", SharedPostgresTestFixture.GRAPHRAG_USER);
            }
            assertLangfuseSentinel(fixture);
        }
    }

    private void createLangfuseSentinel(SharedPostgresTestFixture fixture) throws Exception {
        try (Connection admin = fixture.adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("CREATE TABLE langfuse_marker (value text NOT NULL)");
            statement.execute("INSERT INTO langfuse_marker (value) VALUES ('preserved')");
        }
    }

    private void assertLangfuseSentinel(SharedPostgresTestFixture fixture) throws Exception {
        try (Connection admin = fixture.adminConnection(); Statement statement = admin.createStatement()) {
            assertSingleValue(statement, "SELECT value FROM langfuse_marker", "preserved");
        }
    }

    private void assertProvisioned(SharedPostgresTestFixture fixture) throws Exception {
        try (Connection application = fixture.graphRagConnection();
             Statement statement = application.createStatement();
             ResultSet schema = statement.executeQuery(
                 "SELECT schema_owner FROM information_schema.schemata WHERE schema_name = 'app'"
             )) {
            assertThat(schema.next()).isTrue();
            assertThat(schema.getString(1)).isEqualTo(SharedPostgresTestFixture.GRAPHRAG_USER);
        }

        try (Connection admin = fixture.adminConnection();
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

        assertThatThrownBy(() -> queryAsGraphRag(
            fixture,
            SharedPostgresTestFixture.GRAPHRAG_DATABASE,
            "CREATE ROLE forbidden"
        )).isInstanceOf(SQLException.class);
    }

    private void queryAsGraphRag(
        SharedPostgresTestFixture fixture,
        String database,
        String sql
    ) throws SQLException {
        try (Connection connection = fixture.graphRagConnection(database);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void assertSingleValue(Statement statement, String sql, String expected) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(expected);
            assertThat(result.next()).isFalse();
        }
    }
}
