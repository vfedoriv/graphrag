package io.github.vfedoriv.graphrag.settings.application;

import io.github.vfedoriv.graphrag.indexes.configuration.Neo4jProperties;
import io.github.vfedoriv.graphrag.ai.configuration.ModelProperties;
import io.github.vfedoriv.graphrag.storage.configuration.StorageProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ChunkingProperties;
import io.github.vfedoriv.graphrag.settings.configuration.QueryProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ExtractionProperties;

import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess;

import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.observability.configuration.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.bootstrap.AppProperties;
import io.github.vfedoriv.graphrag.settings.domain.RuntimeSettingOverrideNode;
import io.github.vfedoriv.graphrag.settings.api.model.RuntimeSettingResponse;
import io.github.vfedoriv.graphrag.settings.api.model.RuntimeSettingUpdateRequest;
import io.github.vfedoriv.graphrag.settings.ports.RuntimeSettingOverrideRepository;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.mock.env.MockEnvironment;

class RuntimeSettingsServiceTest {

    @Test
    void fallsBackToStartupDefaultsWhenNoOverridesExist() {
        RuntimeSettingsService service = service(new LinkedHashMap<>());

        assertThat(service.query().maxRows()).isEqualTo(200);
        assertThat(service.query().blockedKeywords()).containsExactly("CREATE", "DELETE");
        assertThat(service.chunking().maxCharacters()).isEqualTo(4000);
        assertThat(service.extraction().maxEntitiesPerChunk()).isEqualTo(40);
        assertThat(service.aiObservation().modelNameTagEnabled()).isTrue();
    }

    @Test
    void validatesPersistsAndAppliesLiveOverridesThroughTypedAccessors() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        RuntimeSettingResponse maxRows = service.update("app.query.max-rows", 25);
        service.update("app.query.blocked-keywords", List.of("CREATE", "MERGE"));
        service.update("app.chunking.max-characters", "1200");
        service.update("app.extraction.max-retries", 4);
        service.update("app.ai.observability.model-name-tag-enabled", false);

        assertThat(maxRows.source()).isEqualTo("override");
        assertThat(maxRows.updateMode()).isEqualTo("live");
        assertThat(maxRows.liveApplied()).isTrue();
        assertThat(maxRows.lifecycleState()).isEqualTo("active");
        assertThat(service.query().maxRows()).isEqualTo(25);
        assertThat(service.query().blockedKeywords()).containsExactly("CREATE", "MERGE");
        assertThat(service.chunking().maxCharacters()).isEqualTo(1200);
        assertThat(service.extraction().maxRetries()).isEqualTo(4);
        assertThat(service.aiObservation().modelNameTagEnabled()).isFalse();
        assertThat(store.get("app.query.max-rows").getValue()).isEqualTo("25");
    }

    @Test
    void canonicalChunkingKeysTakePrecedenceOverCompatibilityAliases() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        service.update("app.chunking.max-tokens", 700);
        assertThat(service.chunking().targetTokens()).isEqualTo(700);

        service.update("app.chunking.target-tokens", 600);
        service.update("app.chunking.max-tokens", 500);

        assertThat(service.chunking().strategy()).isEqualTo("fixed-character");
        assertThat(service.chunking().targetTokens()).isEqualTo(600);
        assertThat(service.chunking().hardCharacterLimit()).isEqualTo(4000);
    }

    @Test
    void reportsRevisionAndExplicitMigrationLifecycleWithoutQueuingWork() {
        RuntimeSettingsService service = service(new LinkedHashMap<>());
        String before = service.effectiveChunkerRevision();

        RuntimeSettingResponse response = service.update("app.chunking.target-tokens", 600);

        assertThat(response.effectiveChunkerRevision())
            .startsWith("chunker_")
            .isNotEqualTo(before);
        assertThat(response.chunkMigrationLifecycle()).isEqualTo("explicit-reprocessing-required");
    }

    @Test
    void invalidChunkingBulkUpdateIsRejectedAtomically() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        assertThatThrownBy(() -> service.update(List.of(
            new RuntimeSettingUpdateRequest("app.chunking.target-tokens", 100),
            new RuntimeSettingUpdateRequest("app.chunking.overlap-tokens", 100)
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("smaller than the effective target");

        assertThat(store).doesNotContainKeys(
            "app.chunking.target-tokens",
            "app.chunking.overlap-tokens"
        );
        assertThat(service.chunking().targetTokens()).isEqualTo(800);
        assertThat(service.chunking().overlapTokens()).isEqualTo(80);
    }

    @Test
    void contextHeaderBudgetsAreValidatedAgainstCompleteEmbeddingLimits() {
        RuntimeSettingsService service = service(new LinkedHashMap<>());

        assertThatThrownBy(() -> service.update(List.of(
            new RuntimeSettingUpdateRequest("app.chunking.target-tokens", 96),
            new RuntimeSettingUpdateRequest("app.chunking.context-header-max-tokens", 96)
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("context-header-max-tokens");

        assertThatThrownBy(() -> service.update(List.of(
            new RuntimeSettingUpdateRequest("app.chunking.hard-character-limit", 512),
            new RuntimeSettingUpdateRequest("app.chunking.context-header-max-characters", 512)
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("context-header-max-characters");
    }

    @Test
    void chunkingCatalogReportsCanonicalLifecycleAndSnapshotRetention() {
        RuntimeSettingsService service = service(new LinkedHashMap<>());

        Map<String, RuntimeSettingResponse> settings = settingsByKey(service);

        assertThat(settings).containsKeys(
            "app.chunking.strategy",
            "app.chunking.target-tokens",
            "app.chunking.overlap-tokens",
            "app.chunking.hard-character-limit",
            "app.chunking.context-header-max-tokens",
            "app.chunking.context-header-max-characters",
            "app.chunking.representation-revision",
            "app.chunking.max-tokens",
            "app.chunking.max-characters"
        );
        assertThat(settings.get("app.chunking.strategy").constraints())
            .containsEntry("enum", List.of("recursive", "fixed-character"));
        assertThat(settings.get("app.chunking.strategy").description())
            .contains("existing chunks retain their snapshotted revision");
        assertThat(settings.get("app.chunking.target-tokens").updateMode()).isEqualTo("live");
    }

    @Test
    void mutableRestartRequiredOverrideIsPersistedAsPendingDesiredValue() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        RuntimeSettingResponse updated = service.update("app.storage.documents-root", "var/other-documents");

        assertThat(updated.currentValue()).isEqualTo("var/other-documents");
        assertThat(updated.defaultValue()).isEqualTo("var/documents");
        assertThat(updated.activeValue()).isEqualTo("var/documents");
        assertThat(updated.source()).isEqualTo("override");
        assertThat(updated.mutable()).isTrue();
        assertThat(updated.liveApplied()).isFalse();
        assertThat(updated.updateMode()).isEqualTo("restart-required");
        assertThat(updated.lifecycleState()).isEqualTo("pending-restart");
        assertThat(store.get("app.storage.documents-root").getValue()).isEqualTo("var/other-documents");
        assertThat(store.get("app.storage.documents-root").getLifecycleState()).isEqualTo("pending-restart");
    }

    @Test
    void restartRequiredOverrideIsActiveWhenStartupDefaultMatchesPersistedValue() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingOverrideNode node = override("app.storage.documents-root", "var/restarted-documents");
        node.setLifecycleState("pending-restart");
        store.put("app.storage.documents-root", node);

        RuntimeSettingsService service = service(store, appProperties("var/restarted-documents"));
        RuntimeSettingResponse setting = settingsByKey(service).get("app.storage.documents-root");

        assertThat(setting.currentValue()).isEqualTo("var/restarted-documents");
        assertThat(setting.activeValue()).isEqualTo("var/restarted-documents");
        assertThat(setting.lifecycleState()).isEqualTo("active");
        assertThat(store.get("app.storage.documents-root").getLifecycleState()).isEqualTo("active");
    }

    @Test
    void clearRestartRequiredOverrideFallsBackToStartupDefault() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);
        service.update("app.storage.documents-root", "var/other-documents");

        RuntimeSettingResponse cleared = service.clear("app.storage.documents-root");

        assertThat(cleared.currentValue()).isEqualTo("var/documents");
        assertThat(cleared.activeValue()).isEqualTo("var/documents");
        assertThat(cleared.source()).isEqualTo("default");
        assertThat(cleared.lifecycleState()).isEqualTo("default");
        assertThat(store).doesNotContainKey("app.storage.documents-root");
    }

    @Test
    void bulkUpdatePersistsAllSubmittedOverridesInRequestOrder() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        List<RuntimeSettingResponse> updated = service.update(List.of(
            new RuntimeSettingUpdateRequest("app.query.max-rows", 25),
            new RuntimeSettingUpdateRequest("app.query.require-limit", false),
            new RuntimeSettingUpdateRequest("app.query.blocked-keywords", List.of("CREATE", "MERGE"))
        ));

        assertThat(updated).extracting(RuntimeSettingResponse::key)
            .containsExactly("app.query.max-rows", "app.query.require-limit", "app.query.blocked-keywords");
        assertThat(updated).extracting(RuntimeSettingResponse::source)
            .containsExactly("override", "override", "override");
        assertThat(service.query().maxRows()).isEqualTo(25);
        assertThat(service.query().requireLimit()).isFalse();
        assertThat(service.query().blockedKeywords()).containsExactly("CREATE", "MERGE");
        assertThat(store.get("app.query.max-rows").getValue()).isEqualTo("25");
        assertThat(store.get("app.query.require-limit").getValue()).isEqualTo("false");
        assertThat(store.get("app.query.blocked-keywords").getValue()).isEqualTo("CREATE,MERGE");
    }

    @Test
    void repeatedUpdateMutatesExistingOverrideNode() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        service.update("app.query.max-rows", 25);
        RuntimeSettingOverrideNode originalNode = store.get("app.query.max-rows");

        RuntimeSettingResponse updated = service.update("app.query.max-rows", 30);

        assertThat(updated.currentValue()).isEqualTo(30);
        assertThat(updated.lifecycleState()).isEqualTo("active");
        assertThat(store.get("app.query.max-rows")).isSameAs(originalNode);
        assertThat(store.get("app.query.max-rows").getValue()).isEqualTo("30");
        assertThat(service.query().maxRows()).isEqualTo(30);
    }

    @Test
    void backfillsMissingOverrideVersionsBeforeReadingPersistedOverrides() {
        RuntimeSettingOverrideNode node = override("app.storage.documents-root", "var/restarted-documents");
        node.setLifecycleState("pending-restart");
        RuntimeSettingOverrideRepository repository = mock(RuntimeSettingOverrideRepository.class);
        when(repository.findById("app.storage.documents-root")).thenReturn(Optional.of(node));
        RuntimeSettingsService service = new RuntimeSettingsService(
            repository,io.github.vfedoriv.graphrag.TestRuntimeSettings.startupDefaults(appProperties("var/restarted-documents")),
            observabilityProperties(),
            environment()
        , new io.github.vfedoriv.graphrag.bootstrap.integration.settings.SettingsChunkRevisionAdapter(new io.github.vfedoriv.graphrag.documents.application.inspection.DocumentChunkRevisionsFacade()));

        RuntimeSettingResponse setting = settingsByKey(service).get("app.storage.documents-root");

        assertThat(setting.lifecycleState()).isEqualTo("active");
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).backfillMissingVersions();
        inOrder.verify(repository).findById("app.storage.documents-root");
    }

    @Test
    void bulkUpdateAllowsMixedLiveAndRestartRequiredSettings() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        List<RuntimeSettingResponse> updated = service.update(List.of(
            new RuntimeSettingUpdateRequest("app.query.max-rows", 25),
            new RuntimeSettingUpdateRequest("app.storage.documents-root", "var/bulk-documents")
        ));

        assertThat(updated).extracting(RuntimeSettingResponse::key)
            .containsExactly("app.query.max-rows", "app.storage.documents-root");
        assertThat(updated).extracting(RuntimeSettingResponse::lifecycleState)
            .containsExactly("active", "pending-restart");
        assertThat(service.query().maxRows()).isEqualTo(25);
        assertThat(store.get("app.storage.documents-root").getValue()).isEqualTo("var/bulk-documents");
    }

    @Test
    void listsExpandedCatalogWithUpdateModesAndProfileResolvedDefaults() {
        RuntimeSettingsService service = service(new LinkedHashMap<>());

        Map<String, RuntimeSettingResponse> settings = settingsByKey(service);

        assertThat(settings).containsKeys(
            "spring.application.name",
            "logging.level.root",
            "spring.ai.model.chat",
            "spring.autoconfigure.exclude",
            "spring.neo4j.uri",
            "spring.datasource.url",
            "spring.datasource.hikari.maximum-pool-size",
            "spring.flyway.default-schema",
            "app.storage.documents-root",
            "spring.servlet.multipart.max-file-size",
            "management.tracing.enabled",
            "management.opentelemetry.tracing.export.otlp.endpoint",
            "app.model.base-url",
            "spring.ai.openai.base-url"
        );
        assertThat(settings.get("spring.application.name").currentValue()).isEqualTo("graphrag-test");
        assertThat(settings.get("logging.level.root").updateMode()).isEqualTo("live");
        assertThat(settings.get("logging.level.root").mutable()).isTrue();
        assertThat(settings.get("logging.level.root").liveApplied()).isTrue();
        assertThat(settings.get("spring.neo4j.uri").updateMode()).isEqualTo("restart-required");
        assertThat(settings.get("spring.neo4j.uri").mutable()).isFalse();
        assertThat(settings.get("spring.neo4j.authentication.password").mutable()).isFalse();
        assertThat(settings.get("spring.datasource.url").updateMode()).isEqualTo("restart-required");
        assertThat(settings.get("spring.datasource.url").mutable()).isFalse();
        assertThat(settings.get("spring.datasource.hikari.maximum-pool-size").updateMode()).isEqualTo("restart-required");
        assertThat(settings.get("spring.datasource.hikari.maximum-pool-size").mutable()).isFalse();
        assertThat(settings.get("app.model.base-url").updateMode()).isEqualTo("profile-managed");
        assertThat(settings.get("app.model.base-url").mutable()).isFalse();
        assertThat(settings.get("spring.application.name").mutable()).isFalse();
        assertThat(settings.get("app.storage.documents-root").updateMode()).isEqualTo("restart-required");
        assertThat(settings.get("app.storage.documents-root").mutable()).isTrue();
        assertThat(settings.get("spring.servlet.multipart.max-file-size").updateMode()).isEqualTo("restart-required");
        assertThat(settings.get("spring.servlet.multipart.max-file-size").mutable()).isTrue();
        assertThat(settings.get("management.tracing.enabled").updateMode()).isEqualTo("restart-required");
        assertThat(settings.get("management.tracing.enabled").mutable()).isTrue();
        assertThat(settings.get("management.opentelemetry.tracing.export.otlp.endpoint").updateMode()).isEqualTo("restart-required");
        assertThat(settings.get("management.opentelemetry.tracing.export.otlp.endpoint").mutable()).isTrue();
        assertThat(settings.get("spring.ai.openai.timeout").updateMode()).isEqualTo("restart-required");
        assertThat(settings.get("spring.ai.openai.timeout").mutable()).isTrue();
        assertThat(settings.get("spring.ai.model.chat").updateMode()).isEqualTo("restart-required");
        assertThat(settings.get("spring.ai.model.chat").mutable()).isFalse();
        assertThat(settings.get("spring.autoconfigure.exclude").updateMode()).isEqualTo("restart-required");
        assertThat(settings.get("spring.autoconfigure.exclude").mutable()).isFalse();
        assertThat(settings.get("app.query.max-rows").updateMode()).isEqualTo("live");
    }

    @Test
    void rootLoggingLevelIsValidatedPersistedAndLiveApplied() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        try {
            RuntimeSettingResponse updated = service.update("logging.level.root", "warn");

            assertThat(updated.currentValue()).isEqualTo("WARN");
            assertThat(updated.updateMode()).isEqualTo("live");
            assertThat(updated.liveApplied()).isTrue();
            assertThat(updated.lifecycleState()).isEqualTo("active");
            assertThat(store.get("logging.level.root").getValue()).isEqualTo("WARN");

            assertThatThrownBy(() -> service.update("logging.level.root", "verbose"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be one of");
        } finally {
            service.update("logging.level.root", "INFO");
        }
    }

    @Test
    void clearingRootLoggingLevelReappliesStartupDefault() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);
        LoggingSystem loggingSystem = LoggingSystem.get(RuntimeSettingsService.class.getClassLoader());

        try {
            service.update("logging.level.root", "WARN");

            RuntimeSettingResponse cleared = service.clear("logging.level.root");

            assertThat(cleared.currentValue()).isEqualTo("INFO");
            assertThat(cleared.activeValue()).isEqualTo("INFO");
            assertThat(cleared.source()).isEqualTo("default");
            assertThat(loggingSystem.getLoggerConfiguration("ROOT").getEffectiveLevel()).isEqualTo(LogLevel.INFO);
        } finally {
            loggingSystem.setLogLevel("ROOT", LogLevel.INFO);
        }
    }

    @Test
    void rejectsReadOnlyAndClearRequestsWithoutPersisting() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        assertThatThrownBy(() -> service.update("spring.neo4j.uri", "bolt://other"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("restart-required");
        assertThatThrownBy(() -> service.clear("spring.neo4j.uri"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("restart-required");

        assertThat(store).isEmpty();
    }

    @Test
    void masksSensitiveValuesInCurrentAndDefaultResponses() {
        RuntimeSettingsService service = service(new LinkedHashMap<>());

        Map<String, RuntimeSettingResponse> settings = settingsByKey(service);

        assertMasked(settings.get("app.model.api-key"));
        assertMasked(settings.get("spring.ai.openai.api-key"));
        assertMasked(settings.get("spring.neo4j.authentication.password"));
        assertMasked(settings.get("spring.datasource.password"));
        assertMasked(settings.get("management.opentelemetry.tracing.export.otlp.headers.Authorization"));
        assertThat(settings.get("app.model.api-key").currentValue().toString()).doesNotContain("secret");
        assertThat(settings.get("app.model.api-key").defaultValue().toString()).doesNotContain("secret");
        assertThat(settings.get("spring.neo4j.authentication.password").currentValue().toString()).doesNotContain("neo4j-secret");
        assertThat(settings.get("spring.neo4j.authentication.password").defaultValue().toString()).doesNotContain("neo4j-secret");
        assertThat(settings.get("spring.datasource.password").currentValue().toString()).doesNotContain("postgres-secret");
        assertThat(settings.get("spring.datasource.password").defaultValue().toString()).doesNotContain("postgres-secret");
        assertThat(settings.get("management.opentelemetry.tracing.export.otlp.headers.Authorization").currentValue().toString()).doesNotContain("Basic raw-secret");
        assertThat(settings.get("management.opentelemetry.tracing.export.otlp.headers.Authorization").defaultValue().toString()).doesNotContain("Basic raw-secret");
    }

    @Test
    void rejectsNonAllowlistedAndInvalidSettingUpdatesWithoutPersisting() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        assertThatThrownBy(() -> service.update("spring.neo4j.pool.max-connection-pool-size", 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not allowlisted");
        assertThatThrownBy(() -> service.update("app.query.max-rows", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("greater than or equal to 1");
        assertThatThrownBy(() -> service.update("app.query.require-limit", "sometimes"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("boolean");

        assertThat(store).isEmpty();
    }

    @Test
    void rejectsInvalidBulkUpdatesWithoutChangingOverrides() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        store.put("app.query.max-rows", override("app.query.max-rows", "50"));
        RuntimeSettingsService service = service(store);

        assertThatThrownBy(() -> service.update(List.of(
            new RuntimeSettingUpdateRequest("app.query.max-rows", 25),
            new RuntimeSettingUpdateRequest("app.query.require-limit", "sometimes")
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("boolean");
        assertThat(store).containsOnlyKeys("app.query.max-rows");
        assertThat(store.get("app.query.max-rows").getValue()).isEqualTo("50");

        assertThatThrownBy(() -> service.update(List.of(
            new RuntimeSettingUpdateRequest("app.query.max-rows", 25),
            new RuntimeSettingUpdateRequest("spring.neo4j.pool.max-connection-pool-size", 10)
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not allowlisted");
        assertThat(store).containsOnlyKeys("app.query.max-rows");
        assertThat(store.get("app.query.max-rows").getValue()).isEqualTo("50");

        assertThatThrownBy(() -> service.update(List.of(
            new RuntimeSettingUpdateRequest("app.query.max-rows", 25),
            new RuntimeSettingUpdateRequest("spring.neo4j.uri", "bolt://other")
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("restart-required");
        assertThat(store).containsOnlyKeys("app.query.max-rows");
        assertThat(store.get("app.query.max-rows").getValue()).isEqualTo("50");

        assertThatThrownBy(() -> service.update(List.of(
            new RuntimeSettingUpdateRequest("app.query.max-rows", 25),
            new RuntimeSettingUpdateRequest("app.query.max-rows", 30)
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate key");
        assertThat(store).containsOnlyKeys("app.query.max-rows");
        assertThat(store.get("app.query.max-rows").getValue()).isEqualTo("50");

        assertThatThrownBy(() -> service.update(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one setting");
        assertThat(store).containsOnlyKeys("app.query.max-rows");
        assertThat(store.get("app.query.max-rows").getValue()).isEqualTo("50");
    }

    @Test
    void clearingOverrideFallsBackToStartupDefault() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        service.update("app.query.max-rows", 25);
        assertThat(service.query().maxRows()).isEqualTo(25);

        RuntimeSettingResponse cleared = service.clear("app.query.max-rows");

        assertThat(cleared.source()).isEqualTo("default");
        assertThat(service.query().maxRows()).isEqualTo(200);
        assertThat(store).doesNotContainKey("app.query.max-rows");
    }

    @Test
    void discoveryExecutionBudgetOverridesAreLiveAndClearToDefaults() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        service.update("app.schema-discovery.max-concurrency", 2);
        service.update("app.schema-discovery.source-timeout-seconds", 7);
        service.update("app.schema-discovery.request-timeout-seconds", 19);
        RuntimeSettingsAccess.DiscoverySettings overridden = service.discovery();

        assertThat(overridden.maxConcurrency()).isEqualTo(2);
        assertThat(overridden.sourceTimeout()).isEqualTo(java.time.Duration.ofSeconds(7));
        assertThat(overridden.requestTimeout()).isEqualTo(java.time.Duration.ofSeconds(19));

        service.clear("app.schema-discovery.max-concurrency");
        service.clear("app.schema-discovery.source-timeout-seconds");
        service.clear("app.schema-discovery.request-timeout-seconds");
        RuntimeSettingsAccess.DiscoverySettings defaults = service.discovery();

        assertThat(defaults.maxConcurrency()).isEqualTo(4);
        assertThat(defaults.sourceTimeout()).isEqualTo(java.time.Duration.ofSeconds(60));
        assertThat(defaults.requestTimeout()).isEqualTo(java.time.Duration.ofSeconds(180));
    }

    @Test
    void advancedSearchSettingsAreTypedSnapshottableAndCrossValidatedAtomically() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        RuntimeSettingsService service = service(store);

        service.update("app.advanced-search.deadline-seconds", 30);
        service.update("app.advanced-search.default-evidence", 8);
        RuntimeSettingsAccess.AdvancedSearchSettings settings = service.advancedSearch();

        assertThat(settings.deadline()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(settings.defaultEvidence()).isEqualTo(8);
        assertThat(settings.defaultIncludeEvidenceText()).isFalse();
        assertThat(settings.maxEvidence()).isEqualTo(20);
        assertThat(settings.planningMaxSubqueries()).isEqualTo(3);
        assertThat(settings.followUpMaxQueries()).isEqualTo(2);
        assertThat(settings.followUpMinimumRemaining()).isEqualTo(java.time.Duration.ofSeconds(5));
        assertThat(settings.synthesisReserve()).isEqualTo(java.time.Duration.ofSeconds(10));
        assertThat(settingsByKey(service).get("app.advanced-search.concurrency").updateMode())
            .isEqualTo("restart-required");
        assertThat(settingsByKey(service).get("app.advanced-search.concurrency").mutable()).isFalse();

        assertThatThrownBy(() -> service.update(List.of(
            new RuntimeSettingUpdateRequest("app.advanced-search.default-evidence", 15),
            new RuntimeSettingUpdateRequest("app.advanced-search.max-evidence", 10)
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("default-evidence");
        assertThat(store).doesNotContainKey("app.advanced-search.max-evidence");
        assertThat(store.get("app.advanced-search.default-evidence").getValue()).isEqualTo("8");

        assertThatThrownBy(() -> service.update("app.advanced-search.deadline-seconds", 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reserves");
        assertThat(store.get("app.advanced-search.deadline-seconds").getValue()).isEqualTo("30");
    }

    @Test
    void legacyHybridOverridesMigrateIdempotentlyWithAdvancedPrecedence() {
        Map<String, RuntimeSettingOverrideNode> store = new LinkedHashMap<>();
        store.put("app.query.hybrid-search-max-candidates",
            override("app.query.hybrid-search-max-candidates", "75"));
        store.put("app.query.hybrid-search-include-chunk-text",
            override("app.query.hybrid-search-include-chunk-text", "true"));
        store.put("app.query.hybrid-search-candidate-multiplier",
            override("app.query.hybrid-search-candidate-multiplier", "7"));
        store.put("app.query.hybrid-search-default-graph-depth",
            override("app.query.hybrid-search-default-graph-depth", "2"));
        store.put("app.advanced-search.max-candidates",
            override("app.advanced-search.max-candidates", "90"));
        RuntimeSettingsService service = service(store);

        service.migrateLegacyHybridOverrides();
        service.migrateLegacyHybridOverrides();

        assertThat(store).doesNotContainKeys(
            "app.query.hybrid-search-max-candidates",
            "app.query.hybrid-search-include-chunk-text",
            "app.query.hybrid-search-candidate-multiplier",
            "app.query.hybrid-search-default-graph-depth"
        );
        assertThat(store.get("app.advanced-search.max-candidates").getValue()).isEqualTo("90");
        assertThat(store.get("app.advanced-search.default-include-evidence-text").getValue()).isEqualTo("true");
        assertThat(service.advancedSearch().defaultIncludeEvidenceText()).isTrue();
    }

    private RuntimeSettingsService service(Map<String, RuntimeSettingOverrideNode> store) {
        return service(store, appProperties());
    }

    private RuntimeSettingsService service(Map<String, RuntimeSettingOverrideNode> store, AppProperties appProperties) {
        RuntimeSettingOverrideRepository repository = mock(RuntimeSettingOverrideRepository.class);
        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        when(repository.save(any(RuntimeSettingOverrideNode.class))).thenAnswer(invocation -> {
            RuntimeSettingOverrideNode node = invocation.getArgument(0);
            store.put(node.getKey(), node);
            return node;
        });
        doAnswer(invocation -> {
            store.remove(invocation.getArgument(0));
            return null;
        }).when(repository).deleteById(anyString());
        return new RuntimeSettingsService(repository,io.github.vfedoriv.graphrag.TestRuntimeSettings.startupDefaults(appProperties), observabilityProperties(), environment(), new io.github.vfedoriv.graphrag.bootstrap.integration.settings.SettingsChunkRevisionAdapter(new io.github.vfedoriv.graphrag.documents.application.inspection.DocumentChunkRevisionsFacade()));
    }

    private RuntimeSettingOverrideNode override(String key, String value) {
        RuntimeSettingOverrideNode node = new RuntimeSettingOverrideNode();
        node.setKey(key);
        node.setValue(value);
        return node;
    }

    private Map<String, RuntimeSettingResponse> settingsByKey(RuntimeSettingsService service) {
        Map<String, RuntimeSettingResponse> settings = new LinkedHashMap<>();
        for (RuntimeSettingResponse setting : service.list()) {
            settings.put(setting.key(), setting);
        }
        return settings;
    }

    @SuppressWarnings("unchecked")
    private void assertMasked(RuntimeSettingResponse setting) {
        assertThat(setting.sensitive()).isTrue();
        assertThat(setting.updateMode()).isEqualTo("sensitive-read-only");
        assertThat((Map<String, Object>) setting.currentValue()).containsEntry("configured", true).containsEntry("masked", true);
        assertThat((Map<String, Object>) setting.defaultValue()).containsEntry("configured", true).containsEntry("masked", true);
    }

    private MockEnvironment environment() {
        return new MockEnvironment()
            .withProperty("spring.application.name", "graphrag-test")
            .withProperty("logging.level.root", "INFO")
            .withProperty("spring.ai.model.chat", "openai")
            .withProperty("spring.ai.model.embedding", "openai")
            .withProperty("spring.autoconfigure.exclude", "")
            .withProperty("spring.neo4j.uri", "bolt://localhost:7687")
            .withProperty("spring.neo4j.authentication.username", "neo4j")
            .withProperty("spring.neo4j.authentication.password", "neo4j-secret")
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5433/graphrag")
            .withProperty("spring.datasource.username", "graphrag")
            .withProperty("spring.datasource.password", "postgres-secret")
            .withProperty("spring.datasource.driver-class-name", "org.postgresql.Driver")
            .withProperty("spring.datasource.hikari.maximum-pool-size", "10")
            .withProperty("spring.datasource.hikari.minimum-idle", "2")
            .withProperty("spring.datasource.hikari.connection-timeout", "30000")
            .withProperty("spring.datasource.hikari.pool-name", "graphrag-pool")
            .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
            .withProperty("spring.jpa.open-in-view", "false")
            .withProperty("spring.jpa.properties.hibernate.default_schema", "app")
            .withProperty("spring.flyway.default-schema", "app")
            .withProperty("spring.flyway.schemas", "app")
            .withProperty("spring.flyway.create-schemas", "true")
            .withProperty("spring.flyway.baseline-on-migrate", "false")
            .withProperty("spring.flyway.validate-on-migrate", "true")
            .withProperty("spring.flyway.clean-disabled", "true")
            .withProperty("spring.servlet.multipart.max-file-size", "100MB")
            .withProperty("spring.servlet.multipart.max-request-size", "100MB")
            .withProperty("management.endpoints.web.exposure.include", "health,metrics")
            .withProperty("management.endpoint.health.probes.enabled", "true")
            .withProperty("management.tracing.enabled", "true")
            .withProperty("management.tracing.export.otlp.enabled", "true")
            .withProperty("management.tracing.sampling.probability", "1.0")
            .withProperty("management.opentelemetry.tracing.export.otlp.endpoint", "http://localhost:3000/api/public/otel/v1/traces")
            .withProperty("management.opentelemetry.tracing.export.otlp.headers.Authorization", "Basic raw-secret")
            .withProperty("management.opentelemetry.tracing.export.otlp.headers.x-langfuse-ingestion-version", "4")
            .withProperty("spring.ai.openai.base-url", "https://api.openai.com/v1")
            .withProperty("spring.ai.openai.api-key", "secret")
            .withProperty("spring.ai.openai.embedding.options.model", "text-embedding-3-small")
            .withProperty("spring.ai.openai.chat.options.model", "gpt-5-mini")
            .withProperty("spring.ai.openai.timeout", "600s")
            .withProperty("spring.ai.openai.chat.timeout", "600s")
            .withProperty("spring.ai.openai.embedding.timeout", "300s")
            .withProperty("spring.ai.chat.observations.log-prompt", "true")
            .withProperty("spring.ai.chat.observations.log-completion", "true");
    }

    private AppProperties appProperties() {
        return appProperties("var/documents");
    }

    private AppProperties appProperties(String documentsRoot) {
        return new AppProperties(
            new Neo4jProperties("neo4j"),
            new ModelProperties("https://api.openai.com/v1", "secret", "text-embedding-3-small", 1536, "gpt-5-mini"),
            new StorageProperties(Path.of(documentsRoot)),
            new ChunkingProperties(800, 80, 4000),
            new QueryProperties(200, 15, true, List.of("CREATE", "DELETE")),
            new ExtractionProperties(40, 80, 2)
        );
    }

    private AiObservabilityProperties observabilityProperties() {
        return new AiObservabilityProperties(true, false, 512, true, 1024, true, true);
    }
}
