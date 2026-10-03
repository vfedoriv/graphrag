package io.github.vfedoriv.graphrag.config;

import io.github.vfedoriv.graphrag.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

@IntegrationTest
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

    @Test
    void profileModeMigrationPreservesExistingProfilesAndAssignments() throws Exception {
        try (SharedPostgresTestFixture fixture = new SharedPostgresTestFixture()) {
            fixture.startWithProvisioningOnInit();
            org.flywaydb.core.api.configuration.FluentConfiguration configuration = Flyway.configure()
                .dataSource(fixture.jdbcUrl(SharedPostgresTestFixture.GRAPHRAG_DATABASE),
                    SharedPostgresTestFixture.GRAPHRAG_USER, SharedPostgresTestFixture.GRAPHRAG_PASSWORD)
                .defaultSchema("app").schemas("app").locations("classpath:db/migration");
            configuration.target("12").load().migrate();
            try (Connection connection = fixture.graphRagConnection(); Statement statement = connection.createStatement()) {
                statement.execute("""
                    INSERT INTO app.ai_profile (id,name,base_url,api_key,chat_model,embedding_model,
                        embedding_dimensions,timeout_seconds,max_retries,default_profile,revision,created_at,updated_at)
                    VALUES ('existing','Existing','https://provider.invalid/v1','preserved-secret','chat','embed',
                        768,60,2,true,7,'2026-01-01T00:00:00Z','2026-01-02T00:00:00Z')
                    """);
                statement.execute("""
                    INSERT INTO app.knowledge_base (id,name,active_ai_profile_id,created_at,updated_at)
                    VALUES ('assigned','Assigned','existing',now(),now())
                    """);
                String before;
                try (ResultSet result = statement.executeQuery("SELECT row_to_json(p)::text FROM app.ai_profile p")) {
                    result.next(); before = result.getString(1);
                }
                configuration.target("latest").load().migrate();
                try (ResultSet result = statement.executeQuery("SELECT row_to_json(p)::jsonb - 'structured_output_mode' FROM app.ai_profile p")) {
                    result.next();
                    assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getString(1)))
                        .isEqualTo(new com.fasterxml.jackson.databind.ObjectMapper().readTree(before));
                }
                assertSingleValue(statement, "SELECT structured_output_mode FROM app.ai_profile WHERE id='existing'", "PORTABLE");
                assertSingleValue(statement, "SELECT active_ai_profile_id FROM app.knowledge_base WHERE id='assigned'", "existing");
                assertThatThrownBy(() -> statement.execute("UPDATE app.ai_profile SET structured_output_mode='INVALID'"))
                    .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.execute("UPDATE app.ai_profile SET structured_output_mode=NULL"))
                    .isInstanceOf(SQLException.class);
            }
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
