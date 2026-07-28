package io.github.vfedoriv.graphrag;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.TestPropertySource;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.persistence.transaction.GraphTransactional;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseService;
import io.github.vfedoriv.graphrag.service.SchemaRegistryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({ TestcontainersConfiguration.class, PersistenceRoutingIntegrationTest.ProbeConfiguration.class })
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration"
})
class PersistenceRoutingIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private PersistenceProbe persistenceProbe;
    @Autowired
    private SchemaDefinitionRepository schemaDefinitionRepository;
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    @Autowired
    private SchemaRegistryService schemaRegistryService;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private HealthContributorRegistry healthContributorRegistry;

    @BeforeEach
    void resetProbeState() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app.transaction_probe (id varchar(100) PRIMARY KEY)");
        jdbcTemplate.execute("TRUNCATE TABLE app.transaction_probe");
        neo4jClient.query("MATCH (n:PersistenceRoutingProbe) DETACH DELETE n").run();
    }

    @Test
    void exposesExplicitPersistenceBeansHealthAndPoolMetrics() {
        assertThat(applicationContext.getBean("transactionManager")).isInstanceOf(JpaTransactionManager.class);
        assertThat(applicationContext.getBean("neo4jTransactionManager"))
            .isInstanceOf(org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager.class);
        assertThat(applicationContext.getBean(Neo4jTemplate.class)).isNotNull();
        assertThat(healthContributorRegistry.getContributor("db")).isNotNull();
        assertThat(meterRegistry.find("jdbc.connections.max").gauge()).isNotNull();
        assertThat(meterRegistry.find("hikaricp.connections.acquire").timer()).isNotNull();
    }

    @Test
    void relationalRollbackDoesNotMutateNeo4j() {
        assertThatThrownBy(() -> persistenceProbe.relationalFailure()).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM app.transaction_probe", Long.class)).isZero();
        assertThat(graphProbeCount()).isZero();
    }

    @Test
    void graphRollbackDoesNotMutatePostgresql() {
        jdbcTemplate.update("INSERT INTO app.transaction_probe (id) VALUES (?)", "preserved");

        assertThatThrownBy(() -> persistenceProbe.graphFailure()).isInstanceOf(IllegalStateException.class);

        assertThat(graphProbeCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM app.transaction_probe", Long.class)).isEqualTo(1L);
    }

    @Test
    void operationalSchemaRepositoryUsesPostgresqlAndIgnoresLegacyGraphNodes() {
        neo4jClient.query("""
            CREATE (:KnowledgeBase:PersistenceRoutingProbe {id: 'routing-kb'})
                -[:USES_SCHEMA]->
                (:SchemaDefinition:PersistenceRoutingProbe {id: 'routing-schema', name: 'routing', version: 1})
            """).run();

        assertThat(schemaDefinitionRepository.findAllByKnowledgeBaseId("routing-kb")).isEmpty();

        knowledgeBaseService.create("routing-kb", "Routing KB");
        SchemaDefinitionNode schema = schemaRegistryService.createSchema(
            """
            {
              "name": "routing-relational",
              "version": 1,
              "nodes": [
                {"label": "Routing", "key": "id",
                 "properties": [{"name": "id", "type": "string"}]}
              ],
              "relationships": []
            }
            """,
            SchemaSourceType.GENERATED,
            "routing-kb"
        );
        List<SchemaDefinitionNode> schemas = schemaDefinitionRepository.findAllByKnowledgeBaseId("routing-kb");
        assertThat(schemas).extracting(SchemaDefinitionNode::getId).containsExactly(schema.getId());
    }

    private long graphProbeCount() {
        return neo4jClient.query("MATCH (n:PersistenceRoutingProbe) RETURN count(n) AS count")
            .fetchAs(Long.class)
            .one()
            .orElse(0L);
    }

    static class PersistenceProbe {

        private final JdbcTemplate jdbcTemplate;
        private final Neo4jClient neo4jClient;

        PersistenceProbe(JdbcTemplate jdbcTemplate, Neo4jClient neo4jClient) {
            this.jdbcTemplate = jdbcTemplate;
            this.neo4jClient = neo4jClient;
        }

        @RelationalTransactional
        public void relationalFailure() {
            jdbcTemplate.update("INSERT INTO app.transaction_probe (id) VALUES (?)", "rolled-back");
            throw new IllegalStateException("relational rollback");
        }

        @GraphTransactional
        public void graphFailure() {
            neo4jClient.query("CREATE (:PersistenceRoutingProbe {id: 'rolled-back'})").run();
            throw new IllegalStateException("graph rollback");
        }
    }

    static class ProbeConfiguration {

        @Bean
        @Primary
        PersistenceProbe persistenceProbe(JdbcTemplate jdbcTemplate, Neo4jClient neo4jClient) {
            return new PersistenceProbe(jdbcTemplate, neo4jClient);
        }
    }
}
