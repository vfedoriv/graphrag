package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.RuntimeSettingOverrideNode;
import io.github.vfedoriv.graphrag.dto.AiProfileResponse;
import io.github.vfedoriv.graphrag.dto.CreateAiProfileRequest;
import io.github.vfedoriv.graphrag.dto.RuntimeSettingResponse;
import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenizerId;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaAiProfileRepository;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaRuntimeSettingOverrideRepository;
import io.github.vfedoriv.graphrag.repository.AiProfileRepository;
import io.github.vfedoriv.graphrag.repository.RuntimeSettingOverrideRepository;
import io.github.vfedoriv.graphrag.service.AiProfileService;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@RelationalIntegrationTest
@TestPropertySource(properties = {
    "spring.datasource.hikari.pool-name=graphrag-relational-test",
    "spring.datasource.hikari.connection-timeout=30000"
})
class SettingsAndAiProfileRelationalIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private RuntimeSettingOverrideRepository settingRepository;
    @Autowired
    private AiProfileRepository aiProfileRepository;
    @Autowired
    private JpaRuntimeSettingOverrideRepository jpaSettingRepository;
    @Autowired
    private JpaAiProfileRepository jpaAiProfileRepository;
    @Autowired
    private RuntimeSettingsService runtimeSettingsService;
    @Autowired
    private AiProfileService aiProfileService;

    private ExecutorService executor;

    @BeforeEach
    void clearRelationalOperationalState() {
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM app.runtime_setting_override");
        jdbcTemplate.update("DELETE FROM app.ai_profile");
    }

    @AfterEach
    void shutDownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void migrationCreatesValidatedVersionedSchemasAndPartialDefaultIndex() {
        List<String> tables = jdbcTemplate.queryForList("""
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'app'
              AND table_name IN ('runtime_setting_override', 'ai_profile')
            ORDER BY table_name
            """, String.class);
        String indexDefinition = jdbcTemplate.queryForObject("""
            SELECT indexdef
            FROM pg_indexes
            WHERE schemaname = 'app'
              AND indexname = 'ai_profile_single_default_idx'
            """, String.class);

        assertThat(tables).containsExactly("ai_profile", "runtime_setting_override");
        assertThat(indexDefinition).contains("UNIQUE").contains("WHERE default_profile");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO app.runtime_setting_override
                (setting_key, setting_value, lifecycle_state, created_at, updated_at, version)
            VALUES ('invalid', 'value', 'unknown', now(), now(), 0)
            """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO app.ai_profile
                (id, name, base_url, chat_model, embedding_model, embedding_dimensions,
                 timeout_seconds, max_retries, default_profile, revision, created_at, updated_at, version)
            VALUES ('invalid', 'Invalid', 'https://example.test', 'chat', 'embed', 0,
                    60, 2, false, 1, now(), now(), 0)
            """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void settingWritesRollbackAtomicallyAndRejectStaleVersions() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            settingRepository.save(setting("app.query.max-rows", "25", "active"));
            settingRepository.save(setting("invalid", "value", "invalid-state"));
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jpaSettingRepository.count()).isZero();

        RuntimeSettingOverrideNode first = settingRepository.save(
            setting("app.query.max-rows", "25", "active")
        );
        RuntimeSettingOverrideNode winner = settingRepository.findById(first.getKey()).orElseThrow();
        RuntimeSettingOverrideNode stale = settingRepository.findById(first.getKey()).orElseThrow();
        winner.setValue("30");
        settingRepository.save(winner);
        stale.setValue("35");

        assertThatThrownBy(() -> settingRepository.save(stale))
            .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(settingRepository.findById(first.getKey()).orElseThrow().getValue()).isEqualTo("30");
    }

    @Test
    void settingsPreserveLifecycleBehaviorAndMaskDeploymentCredentials() {
        RuntimeSettingResponse updated = runtimeSettingsService.update(
            "app.storage.documents-root",
            "var/next-documents"
        );
        Map<String, RuntimeSettingResponse> settings = runtimeSettingsService.list().stream()
            .collect(java.util.stream.Collectors.toMap(RuntimeSettingResponse::key, setting -> setting));

        assertThat(updated.lifecycleState()).isEqualTo("pending-restart");
        assertThat(updated.activeValue()).isNotEqualTo(updated.currentValue());
        assertThat(settings.get("spring.datasource.url").mutable()).isFalse();
        assertThat(settings.get("spring.datasource.hikari.maximum-pool-size").mutable()).isFalse();
        assertThat(settings.get("spring.jpa.properties.hibernate.default_schema").currentValue()).isEqualTo("app");
        assertThat(settings.get("spring.datasource.password").sensitive()).isTrue();
        assertThat(settings.get("spring.datasource.password").currentValue().toString())
            .doesNotContain("graphrag-test-password");
    }

    @Test
    void profilesPersistSecretsWithoutReturningThemAndRejectStaleVersions() {
        AiProfileResponse response = aiProfileService.create(request("profile-1", false, "secret-profile-key"));
        AiProfileNode winner = aiProfileRepository.findById("profile-1").orElseThrow();
        AiProfileNode stale = aiProfileRepository.findById("profile-1").orElseThrow();
        winner.setName("Winner");
        winner.setRevision(2);
        winner.setUpdatedAt(Instant.now());
        aiProfileRepository.save(winner);
        stale.setName("Stale");
        stale.setRevision(2);
        stale.setUpdatedAt(Instant.now());

        assertThat(response.apiKeyConfigured()).isTrue();
        assertThat(response.tokenizerId()).isEqualTo(TokenizerId.CL100K_BASE);
        assertThat(response.resolvedTokenizerId()).isEqualTo(TokenizerId.CL100K_BASE);
        assertThat(response.toString()).doesNotContain("secret-profile-key");
        assertThat(aiProfileRepository.findById("profile-1").orElseThrow().getApiKey())
            .isEqualTo("secret-profile-key");
        assertThat(aiProfileRepository.findById("profile-1").orElseThrow().getTokenizerId().value())
            .isEqualTo(TokenizerId.CL100K_BASE);
        assertThatThrownBy(() -> aiProfileRepository.save(stale))
            .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(aiProfileRepository.findById("profile-1").orElseThrow().getName()).isEqualTo("Winner");
    }

    @Test
    void concurrentDefaultSelectionCommitsOnlyOneProfile() throws Exception {
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AiProfileResponse>> futures = new ArrayList<>();
        futures.add(executor.submit(() -> createDefaultAfterBarrier("default-a", ready, start)));
        futures.add(executor.submit(() -> createDefaultAfterBarrier("default-b", ready, start)));
        ready.await();
        start.countDown();

        int successes = 0;
        int conflicts = 0;
        for (Future<AiProfileResponse> future : futures) {
            try {
                future.get();
                successes++;
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(ConflictException.class);
                conflicts++;
            }
        }

        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);
        assertThat(jpaAiProfileRepository.count()).isEqualTo(1);
        assertThat(jpaAiProfileRepository.findFirstByDefaultProfileTrue()).isPresent();
    }

    @Test
    void concurrentDefaultSeedingIsIdempotent() throws Exception {
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<AiProfileNode> first = executor.submit(() -> {
            start.await();
            return aiProfileService.seedDefaultProfile();
        });
        Future<AiProfileNode> second = executor.submit(() -> {
            start.await();
            return aiProfileService.seedDefaultProfile();
        });
        start.countDown();

        assertThat(first.get().getId()).isEqualTo(AiProfileService.DEFAULT_PROFILE_ID);
        assertThat(second.get().getId()).isEqualTo(AiProfileService.DEFAULT_PROFILE_ID);
        assertThat(jpaAiProfileRepository.count()).isEqualTo(1);
        assertThat(jpaAiProfileRepository.findFirstByDefaultProfileTrue()).isPresent();
    }

    @Autowired
    private io.github.vfedoriv.graphrag.knowledgebase.ports.OwnedDocumentState ownedDocuments;
    @Autowired
    private io.github.vfedoriv.graphrag.ai.ports.ProfileAssignments assignments;

    @Test
    void documentCountAndProfileAssignmentsParticipateInCallerTransaction() {
        aiProfileService.create(request("assigned", false, "secret"));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                INSERT INTO app.knowledge_base (id, name, active_ai_profile_id, created_at, updated_at)
                VALUES ('transaction-kb', 'Transaction', 'assigned', now(), now())
                """);
            jdbcTemplate.update("""
                INSERT INTO app.document_upload (id, knowledge_base_id, size_bytes, sha256, status, uploaded_at)
                VALUES ('transaction-doc', 'transaction-kb', 3, ?, 'UPLOADED', now())
                """, "a".repeat(64));
            assertThat(ownedDocuments.countByKnowledgeBaseId("transaction-kb")).isEqualTo(1);
            assertThat(assignments.exists("assigned")).isTrue();
            assertThat(assignments.knowledgeBaseIds("assigned")).containsExactly("transaction-kb");
            assertThatThrownBy(() -> aiProfileService.delete("assigned")).isInstanceOf(ConflictException.class);
            status.setRollbackOnly();
        });
        assertThat(ownedDocuments.countByKnowledgeBaseId("transaction-kb")).isZero();
        assertThat(assignments.exists("assigned")).isFalse();
        assertThat(assignments.knowledgeBaseIds("assigned")).isEmpty();
        assertThat(aiProfileRepository.findById("assigned")).isPresent();
    }

    private AiProfileResponse createDefaultAfterBarrier(
        String id,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return aiProfileService.create(request(id, true, null));
    }

    private RuntimeSettingOverrideNode setting(String key, String value, String lifecycleState) {
        Instant now = Instant.now();
        RuntimeSettingOverrideNode setting = new RuntimeSettingOverrideNode();
        setting.setKey(key);
        setting.setValue(value);
        setting.setLifecycleState(lifecycleState);
        setting.setCreatedAt(now);
        setting.setUpdatedAt(now);
        return setting;
    }

    private CreateAiProfileRequest request(String id, boolean defaultProfile, String apiKey) {
        return new CreateAiProfileRequest(
            id,
            "Profile " + id,
            "https://profiles.example/v1",
            apiKey,
            "chat-model",
            "embedding-model",
            TokenizerId.CL100K_BASE,
            768,
            30,
            1,
            defaultProfile
        );
    }
}
