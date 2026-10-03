package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSourceType;
import io.github.vfedoriv.graphrag.http.contracts.ConflictException;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService;
import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@RelationalIntegrationTest
class KnowledgeBaseSchemaRelationalIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired
    private SchemaRegistryService schemaRegistryService;

    @Test
    void enforcesSchemaIdentityAndAssociationOwnership() {
        String suffix = UUID.randomUUID().toString();
        String schemaName = "identity-" + suffix;
        SchemaDefinitionNode schema = schemaRegistryService.createSchema(
            schemaJson(schemaName),
            SchemaSourceType.GENERATED
        );

        assertThatThrownBy(() -> schemaRegistryService.createSchema(
            schemaJson(schemaName),
            SchemaSourceType.PREDEFINED
        )).isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO app.knowledge_base_schema
                (knowledge_base_id, schema_id, active, created_at, updated_at, version)
            VALUES (?, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """,
            "missing-" + suffix,
            schema.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesKnowledgeBaseProfileForeignKey() {
        String id = "invalid-profile-kb-" + UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO app.knowledge_base
                (id, name, active_ai_profile_id, created_at, updated_at, version)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """,
            id,
            id,
            "missing-profile-" + id
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void serializesConcurrentSchemaActivation() throws Exception {
        String knowledgeBaseId = "activation-" + UUID.randomUUID();
        knowledgeBaseService.create(knowledgeBaseId, knowledgeBaseId);
        SchemaDefinitionNode first = schemaRegistryService.createSchema(
            schemaJson("first-" + knowledgeBaseId),
            SchemaSourceType.GENERATED,
            knowledgeBaseId
        );
        SchemaDefinitionNode second = schemaRegistryService.createSchema(
            schemaJson("second-" + knowledgeBaseId),
            SchemaSourceType.GENERATED,
            knowledgeBaseId
        );
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Void> firstResult = executor.submit(activation(start, knowledgeBaseId, first.getId()));
            Future<Void> secondResult = executor.submit(activation(start, knowledgeBaseId, second.getId()));
            start.countDown();
            firstResult.get();
            secondResult.get();
        }

        Long activeCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM app.knowledge_base_schema WHERE knowledge_base_id = ? AND active",
            Long.class,
            knowledgeBaseId
        );
        KnowledgeBaseNode reloaded = knowledgeBaseRepository.findById(knowledgeBaseId).orElseThrow();
        assertThat(activeCount).isEqualTo(1L);
        assertThat(reloaded.getActiveSchemaId()).isIn(first.getId(), second.getId());
    }

    @Test
    void conditionalProfileAssignmentAllowsOnlyOneConcurrentWinner() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String knowledgeBaseId = "profile-cas-" + suffix;
        knowledgeBaseService.create(knowledgeBaseId, knowledgeBaseId);
        insertProfile("profile-a-" + suffix);
        insertProfile("profile-b-" + suffix);
        KnowledgeBaseNode before = knowledgeBaseRepository.findById(knowledgeBaseId).orElseThrow();
        CountDownLatch start = new CountDownLatch(1);

        List<Boolean> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return knowledgeBaseRepository.assignAiProfile(
                    knowledgeBaseId,
                    before.getVersion(),
                    "profile-a-" + suffix
                );
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return knowledgeBaseRepository.assignAiProfile(
                    knowledgeBaseId,
                    before.getVersion(),
                    "profile-b-" + suffix
                );
            });
            start.countDown();
            results = List.of(first.get(), second.get());
        }

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(knowledgeBaseRepository.findById(knowledgeBaseId).orElseThrow().getActiveAiProfileId())
            .isIn("profile-a-" + suffix, "profile-b-" + suffix);
    }

    private Callable<Void> activation(CountDownLatch start, String knowledgeBaseId, String schemaId) {
        return () -> {
            start.await();
            schemaRegistryService.activateSchema(knowledgeBaseId, schemaId);
            return null;
        };
    }

    private void insertProfile(String id) {
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            INSERT INTO app.ai_profile
                (id, name, base_url, chat_model, embedding_model, embedding_dimensions,
                 timeout_seconds, max_retries, default_profile, revision, created_at, updated_at, version)
            VALUES (?, ?, 'https://profiles.example/v1', 'chat', 'embedding', 1536,
                    30, 1, false, 1, ?, ?, 0)
            """,
            id,
            id,
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private String schemaJson(String name) {
        return """
            {
              "name": "%s",
              "version": 1,
              "nodes": [
                {"label": "Contract", "key": "contractId",
                 "properties": [{"name": "contractId", "type": "string"}]}
              ],
              "relationships": []
            }
            """.formatted(name);
    }
}
