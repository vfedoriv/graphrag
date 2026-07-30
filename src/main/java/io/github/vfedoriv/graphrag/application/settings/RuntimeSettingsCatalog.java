package io.github.vfedoriv.graphrag.application.settings;

import io.github.vfedoriv.graphrag.application.settings.RuntimeSettingDefinition.SettingType;
import io.github.vfedoriv.graphrag.application.settings.RuntimeSettingDefinition.UpdateMode;
import io.github.vfedoriv.graphrag.config.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.config.AppProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.core.env.Environment;

public final class RuntimeSettingsCatalog {

    private final RuntimeSettingCodecs codecs;
    private final RuntimeSettingLiveAppliers liveAppliers;
    private final Environment environment;

    public RuntimeSettingsCatalog(
        RuntimeSettingCodecs codecs,
        RuntimeSettingLiveAppliers liveAppliers,
        Environment environment
    ) {
        this.codecs = codecs;
        this.liveAppliers = liveAppliers;
        this.environment = environment;
    }

    public Map<String, RuntimeSettingDefinition> build(
        AppProperties appProperties,
        AiObservabilityProperties observabilityProperties,
        String documentsRoot
    ) {
        Map<String, RuntimeSettingDefinition> map = new LinkedHashMap<>();
        addInt(map, "app.query.max-rows", "query", appProperties.query().maxRows(), 1);
        addInt(map, "app.query.timeout-seconds", "query", appProperties.query().timeoutSeconds(), 1);
        addBool(map, "app.query.require-limit", "query", appProperties.query().requireLimit());
        addStringList(map, "app.query.blocked-keywords", "query", appProperties.query().blockedKeywords());
        addInt(map, "app.query.hybrid-search-default-top-k", "query", appProperties.query().hybridSearchDefaultTopK(), 1);
        addInt(map, "app.query.hybrid-search-max-top-k", "query", appProperties.query().hybridSearchMaxTopK(), 1);
        addInt(map, "app.query.hybrid-search-candidate-multiplier", "query", appProperties.query().hybridSearchCandidateMultiplier(), 1);
        addInt(map, "app.query.hybrid-search-max-candidates", "query", appProperties.query().hybridSearchMaxCandidates(), 1);
        addInt(map, "app.query.hybrid-search-default-graph-depth", "query", appProperties.query().hybridSearchDefaultGraphDepth(), 0);
        addInt(map, "app.query.hybrid-search-max-graph-depth", "query", appProperties.query().hybridSearchMaxGraphDepth(), 0);
        addBool(map, "app.query.hybrid-search-include-chunk-text", "query", appProperties.query().hybridSearchIncludeChunkText());
        addLiveString(
            map,
            "app.chunking.strategy",
            "chunking",
            appProperties.chunking().effectiveStrategy(),
            Map.of("enum", List.of("fixed-character")),
            value -> codecs.enumString("app.chunking.strategy", value, Set.of("fixed-character")),
            value -> { },
            "Selects the versioned strategy for subsequent processing; existing chunks retain their snapshotted revision."
        );
        addChunkingInt(
            map,
            "app.chunking.target-tokens",
            appProperties.chunking().effectiveTargetTokens(),
            1,
            "Canonical target token count for subsequent processing."
        );
        addChunkingInt(
            map,
            "app.chunking.overlap-tokens",
            appProperties.chunking().overlapTokens(),
            0,
            "Canonical overlap token count; it must remain smaller than the effective target."
        );
        addChunkingInt(
            map,
            "app.chunking.hard-character-limit",
            appProperties.chunking().effectiveHardCharacterLimit(),
            1,
            "Hard provider-safety guard for chunk characters."
        );
        addChunkingInt(
            map,
            "app.chunking.max-tokens",
            appProperties.chunking().maxTokens(),
            1,
            "Compatibility alias for app.chunking.target-tokens; the canonical key takes precedence."
        );
        addChunkingInt(
            map,
            "app.chunking.max-characters",
            appProperties.chunking().maxCharacters(),
            1,
            "Compatibility alias for app.chunking.hard-character-limit; the canonical key takes precedence."
        );
        addInt(map, "app.extraction.max-entities-per-chunk", "extraction", appProperties.extraction().maxEntitiesPerChunk(), 1);
        addInt(map, "app.extraction.max-relationships-per-chunk", "extraction", appProperties.extraction().maxRelationshipsPerChunk(), 1);
        addInt(map, "app.extraction.max-retries", "extraction", appProperties.extraction().maxRetries(), 0);
        addInt(map, "app.schema-discovery.max-sources", "schema-discovery", envInt("app.schema-discovery.max-sources", 12), 1);
        addInt(map, "app.schema-discovery.max-source-bytes", "schema-discovery", envInt("app.schema-discovery.max-source-bytes", 5242880), 1);
        addInt(map, "app.schema-discovery.max-total-bytes", "schema-discovery", envInt("app.schema-discovery.max-total-bytes", 20971520), 1);
        addInt(map, "app.schema-discovery.max-source-characters", "schema-discovery", envInt("app.schema-discovery.max-source-characters", 200000), 1);
        addInt(map, "app.schema-discovery.max-total-characters", "schema-discovery", envInt("app.schema-discovery.max-total-characters", 600000), 1);
        addInt(map, "app.schema-discovery.chunk-characters", "schema-discovery", envInt("app.schema-discovery.chunk-characters", 12000), 1);
        addInt(map, "app.schema-discovery.max-chunks-per-source", "schema-discovery", envInt("app.schema-discovery.max-chunks-per-source", 20), 1);
        addInt(map, "app.schema-discovery.max-concurrency", "schema-discovery", envInt("app.schema-discovery.max-concurrency", 4), 1);
        addInt(map, "app.schema-discovery.source-timeout-seconds", "schema-discovery", envInt("app.schema-discovery.source-timeout-seconds", 60), 1);
        addInt(map, "app.schema-discovery.request-timeout-seconds", "schema-discovery", envInt("app.schema-discovery.request-timeout-seconds", 180), 1);
        addBool(map, "app.ai.observability.enabled", "ai-observability", observabilityProperties.enabled());
        addBool(map, "app.ai.observability.content-capture-enabled", "ai-observability", observabilityProperties.contentCaptureEnabled());
        addInt(map, "app.ai.observability.max-attribute-length", "ai-observability", observabilityProperties.maxAttributeLength(), 1);
        addBool(map, "app.ai.observability.input-output-content-enabled", "ai-observability", observabilityProperties.inputOutputContentEnabled());
        addInt(map, "app.ai.observability.max-input-output-length", "ai-observability", observabilityProperties.maxInputOutputLength(), 1);
        addBool(map, "app.ai.observability.model-name-tag-enabled", "ai-observability", observabilityProperties.modelNameTagEnabled());
        addBool(map, "app.ai.observability.schema-name-tag-enabled", "ai-observability", observabilityProperties.schemaNameTagEnabled());
        addReadOnlyString(map, "spring.application.name", "application", env("spring.application.name"), UpdateMode.READ_ONLY, false, "Application identity is informational.");
        addLiveString(map, "logging.level.root", "logging", envOrDefault("logging.level.root", "INFO"),
            Map.of("enum", List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "OFF")),
            value -> codecs.loggingLevel("logging.level.root", value), liveAppliers::rootLoggingLevel,
            "Root logging level is applied through the Spring Boot logging system.");
        addReadOnlyString(map, "spring.ai.model.chat", "spring-ai", env("spring.ai.model.chat"), UpdateMode.RESTART_REQUIRED, false, "Spring AI model bootstrap is bound during startup.");
        addReadOnlyString(map, "spring.ai.model.embedding", "spring-ai", env("spring.ai.model.embedding"), UpdateMode.RESTART_REQUIRED, false, "Spring AI model bootstrap is bound during startup.");
        addReadOnlyString(map, "spring.autoconfigure.exclude", "spring", env("spring.autoconfigure.exclude"), UpdateMode.RESTART_REQUIRED, false, "Auto-configuration exclusions are evaluated during startup.");
        addReadOnlyString(map, "spring.neo4j.uri", "neo4j", env("spring.neo4j.uri"), UpdateMode.RESTART_REQUIRED, false, "Neo4j connectivity is managed by startup-created infrastructure.");
        addReadOnlyString(map, "spring.neo4j.authentication.username", "neo4j", env("spring.neo4j.authentication.username"), UpdateMode.RESTART_REQUIRED, false, "Neo4j authentication is managed by startup-created infrastructure.");
        addReadOnlyString(map, "spring.neo4j.authentication.password", "neo4j", env("spring.neo4j.authentication.password"), UpdateMode.SENSITIVE_READ_ONLY, true, "Neo4j credentials are sensitive and startup-bound.");
        addReadOnlyString(map, "app.neo4j.database", "neo4j", appProperties.neo4j().database(), UpdateMode.RESTART_REQUIRED, false, "Neo4j database selection is bound to repository infrastructure.");
        addReadOnlyString(map, "spring.datasource.url", "postgresql", env("spring.datasource.url"), UpdateMode.RESTART_REQUIRED, false, "PostgreSQL connectivity is managed by startup-created infrastructure.");
        addReadOnlyString(map, "spring.datasource.username", "postgresql", env("spring.datasource.username"), UpdateMode.RESTART_REQUIRED, false, "PostgreSQL authentication is managed by startup-created infrastructure.");
        addReadOnlyString(map, "spring.datasource.password", "postgresql", env("spring.datasource.password"), UpdateMode.SENSITIVE_READ_ONLY, true, "PostgreSQL credentials are sensitive and startup-bound.");
        addReadOnlyString(map, "spring.datasource.driver-class-name", "postgresql", env("spring.datasource.driver-class-name"), UpdateMode.RESTART_REQUIRED, false, "PostgreSQL driver selection is managed by startup-created infrastructure.");
        addReadOnlyString(map, "spring.datasource.hikari.maximum-pool-size", "postgresql", env("spring.datasource.hikari.maximum-pool-size"), UpdateMode.RESTART_REQUIRED, false, "PostgreSQL pool sizing is deployment-managed and applied when the datasource starts.");
        addReadOnlyString(map, "spring.datasource.hikari.minimum-idle", "postgresql", env("spring.datasource.hikari.minimum-idle"), UpdateMode.RESTART_REQUIRED, false, "PostgreSQL pool sizing is deployment-managed and applied when the datasource starts.");
        addReadOnlyString(map, "spring.datasource.hikari.connection-timeout", "postgresql", env("spring.datasource.hikari.connection-timeout"), UpdateMode.RESTART_REQUIRED, false, "PostgreSQL pool acquisition behavior is deployment-managed.");
        addReadOnlyString(map, "spring.datasource.hikari.pool-name", "postgresql", env("spring.datasource.hikari.pool-name"), UpdateMode.RESTART_REQUIRED, false, "PostgreSQL pool identity is deployment-managed.");
        addReadOnlyString(map, "spring.jpa.hibernate.ddl-auto", "postgresql", env("spring.jpa.hibernate.ddl-auto"), UpdateMode.RESTART_REQUIRED, false, "Hibernate schema validation is fixed at startup.");
        addReadOnlyString(map, "spring.jpa.open-in-view", "postgresql", env("spring.jpa.open-in-view"), UpdateMode.RESTART_REQUIRED, false, "Open-session-in-view is fixed at startup.");
        addReadOnlyString(map, "spring.jpa.properties.hibernate.default_schema", "postgresql", env("spring.jpa.properties.hibernate.default_schema"), UpdateMode.RESTART_REQUIRED, false, "The PostgreSQL application schema is deployment-managed.");
        addReadOnlyString(map, "spring.flyway.default-schema", "postgresql", env("spring.flyway.default-schema"), UpdateMode.RESTART_REQUIRED, false, "Flyway schema ownership is fixed at startup.");
        addReadOnlyString(map, "spring.flyway.schemas", "postgresql", env("spring.flyway.schemas"), UpdateMode.RESTART_REQUIRED, false, "Flyway managed schemas are deployment-managed.");
        addReadOnlyString(map, "spring.flyway.create-schemas", "postgresql", env("spring.flyway.create-schemas"), UpdateMode.RESTART_REQUIRED, false, "Flyway schema creation is configured at startup.");
        addReadOnlyString(map, "spring.flyway.baseline-on-migrate", "postgresql", env("spring.flyway.baseline-on-migrate"), UpdateMode.RESTART_REQUIRED, false, "Flyway baselining remains disabled to reject unmanaged schemas.");
        addReadOnlyString(map, "spring.flyway.validate-on-migrate", "postgresql", env("spring.flyway.validate-on-migrate"), UpdateMode.RESTART_REQUIRED, false, "Flyway validation is deployment-managed.");
        addReadOnlyString(map, "spring.flyway.clean-disabled", "postgresql", env("spring.flyway.clean-disabled"), UpdateMode.RESTART_REQUIRED, false, "Flyway cleanup protection is deployment-managed.");
        addRestartRequiredString(map, "app.storage.documents-root", "storage", documentsRoot, "Document storage root changes are persisted for the next backend restart.");
        addRestartRequiredString(map, "spring.servlet.multipart.max-file-size", "multipart", env("spring.servlet.multipart.max-file-size"), "Multipart limits are applied by web infrastructure.");
        addRestartRequiredString(map, "spring.servlet.multipart.max-request-size", "multipart", env("spring.servlet.multipart.max-request-size"), "Multipart limits are applied by web infrastructure.");
        addRestartRequiredString(map, "management.endpoints.web.exposure.include", "actuator", env("management.endpoints.web.exposure.include"), "Actuator exposure is configured at startup.");
        addRestartRequiredString(map, "management.endpoint.health.probes.enabled", "actuator", env("management.endpoint.health.probes.enabled"), "Health probe configuration is bound during startup.");
        addRestartRequiredString(map, "management.tracing.enabled", "tracing", env("management.tracing.enabled"), "Tracing infrastructure is configured at startup.");
        addRestartRequiredString(map, "management.tracing.export.otlp.enabled", "tracing", env("management.tracing.export.otlp.enabled"), "OTLP exporter infrastructure is configured at startup.");
        addRestartRequiredString(map, "management.tracing.sampling.probability", "tracing", env("management.tracing.sampling.probability"), "Tracing sampling is configured at startup.");
        addRestartRequiredString(map, "management.opentelemetry.tracing.export.otlp.endpoint", "opentelemetry", env("management.opentelemetry.tracing.export.otlp.endpoint"), "OTLP exporter endpoint is configured at startup.");
        addReadOnlyString(map, "management.opentelemetry.tracing.export.otlp.headers.Authorization", "opentelemetry", env("management.opentelemetry.tracing.export.otlp.headers.Authorization"), UpdateMode.SENSITIVE_READ_ONLY, true, "OTLP authorization headers are sensitive and startup-bound.");
        addRestartRequiredString(map, "management.opentelemetry.tracing.export.otlp.headers.x-langfuse-ingestion-version", "opentelemetry", env("management.opentelemetry.tracing.export.otlp.headers.x-langfuse-ingestion-version"), "OTLP exporter headers are configured at startup.");
        addReadOnlyString(map, "app.model.base-url", "ai-provider", appProperties.model().baseUrl(), UpdateMode.PROFILE_MANAGED, false, "Use AI profile management to change provider behavior.");
        addReadOnlyString(map, "app.model.api-key", "ai-provider", appProperties.model().apiKey(), UpdateMode.SENSITIVE_READ_ONLY, true, "Use AI profile management to change provider secrets.");
        addReadOnlyString(map, "app.model.embedding-model", "ai-provider", appProperties.model().embeddingModel(), UpdateMode.PROFILE_MANAGED, false, "Use AI profile management to change provider behavior.");
        addReadOnlyString(map, "app.model.embedding-dimensions", "ai-provider", Integer.toString(appProperties.model().embeddingDimensions()), UpdateMode.PROFILE_MANAGED, false, "Use AI profile management to change provider behavior.");
        addReadOnlyString(map, "app.model.chat-model", "ai-provider", appProperties.model().chatModel(), UpdateMode.PROFILE_MANAGED, false, "Use AI profile management to change provider behavior.");
        addReadOnlyString(map, "spring.ai.openai.base-url", "spring-ai-openai", env("spring.ai.openai.base-url"), UpdateMode.PROFILE_MANAGED, false, "Derived from AI provider defaults; use AI profile management for workflow changes.");
        addReadOnlyString(map, "spring.ai.openai.api-key", "spring-ai-openai", env("spring.ai.openai.api-key"), UpdateMode.SENSITIVE_READ_ONLY, true, "Derived API key is sensitive; use AI profile management for workflow changes.");
        addReadOnlyString(map, "spring.ai.openai.embedding.options.model", "spring-ai-openai", env("spring.ai.openai.embedding.options.model"), UpdateMode.PROFILE_MANAGED, false, "Derived from AI provider defaults; use AI profile management for workflow changes.");
        addReadOnlyString(map, "spring.ai.openai.chat.options.model", "spring-ai-openai", env("spring.ai.openai.chat.options.model"), UpdateMode.PROFILE_MANAGED, false, "Derived from AI provider defaults; use AI profile management for workflow changes.");
        addRestartRequiredString(map, "spring.ai.openai.timeout", "spring-ai-openai", env("spring.ai.openai.timeout"), "Spring AI client timeout is configured at startup.");
        addRestartRequiredString(map, "spring.ai.openai.chat.timeout", "spring-ai-openai", env("spring.ai.openai.chat.timeout"), "Spring AI client timeout is configured at startup.");
        addRestartRequiredString(map, "spring.ai.openai.embedding.timeout", "spring-ai-openai", env("spring.ai.openai.embedding.timeout"), "Spring AI client timeout is configured at startup.");
        addRestartRequiredString(map, "spring.ai.chat.observations.log-prompt", "spring-ai-observability", env("spring.ai.chat.observations.log-prompt"), "Spring AI observation logging is configured at startup.");
        addRestartRequiredString(map, "spring.ai.chat.observations.log-completion", "spring-ai-observability", env("spring.ai.chat.observations.log-completion"), "Spring AI observation logging is configured at startup.");
        return Map.copyOf(map);
    }

    private void addInt(Map<String, RuntimeSettingDefinition> map, String key, String category, int defaultValue, int min) {
        put(map, key, category, SettingType.INTEGER, defaultValue, true, true, false, Map.of("min", min),
            value -> codecs.integer(key, value, min), Objects::toString, Function.identity(), value -> { }, UpdateMode.LIVE, null);
    }

    private void addChunkingInt(
        Map<String, RuntimeSettingDefinition> map,
        String key,
        int defaultValue,
        int min,
        String description
    ) {
        put(
            map,
            key,
            "chunking",
            SettingType.INTEGER,
            defaultValue,
            true,
            true,
            false,
            Map.of("min", min),
            value -> codecs.integer(key, value, min),
            Objects::toString,
            Function.identity(),
            value -> { },
            UpdateMode.LIVE,
            null,
            description
        );
    }

    private void addBool(Map<String, RuntimeSettingDefinition> map, String key, String category, boolean defaultValue) {
        put(map, key, category, SettingType.BOOLEAN, defaultValue, true, true, false, Map.of(),
            value -> codecs.bool(key, value), Objects::toString, Function.identity(), value -> { }, UpdateMode.LIVE, null);
    }

    private void addStringList(Map<String, RuntimeSettingDefinition> map, String key, String category, List<String> defaultValue) {
        put(map, key, category, SettingType.STRING_LIST, List.copyOf(defaultValue), true, true, false, Map.of("minItems", 1),
            value -> codecs.stringList(key, value), value -> String.join(",", codecs.castStringList(value)), Function.identity(),
            value -> { }, UpdateMode.LIVE, null);
    }

    private void addLiveString(Map<String, RuntimeSettingDefinition> map, String key, String category, String defaultValue,
        Map<String, Object> constraints, Function<Object, Object> parser, Consumer<Object> applier, String description) {
        put(map, key, category, SettingType.STRING, defaultValue == null ? "" : parser.apply(defaultValue), true, true, false,
            constraints, parser, Objects::toString, Function.identity(), applier, UpdateMode.LIVE, null, description);
    }

    private void addRestartRequiredString(Map<String, RuntimeSettingDefinition> map, String key, String category,
        String defaultValue, String reason) {
        put(map, key, category, SettingType.STRING, defaultValue == null ? "" : defaultValue, true, false, false,
            Map.of("minLength", 1), value -> codecs.nonBlankString(key, value), Objects::toString, Function.identity(),
            value -> { }, UpdateMode.RESTART_REQUIRED, reason);
    }

    private void addReadOnlyString(Map<String, RuntimeSettingDefinition> map, String key, String category, String defaultValue,
        UpdateMode updateMode, boolean sensitive, String reason) {
        put(map, key, category, SettingType.STRING, defaultValue == null ? "" : defaultValue, false, false, sensitive,
            Map.of(), String::valueOf, Objects::toString, sensitive ? codecs::masked : Function.identity(),
            value -> { }, updateMode, reason);
    }

    private void put(Map<String, RuntimeSettingDefinition> map, String key, String category, SettingType type,
        Object defaultValue, boolean mutable, boolean liveApplied, boolean sensitive, Map<String, Object> constraints,
        Function<Object, Object> parser, Function<Object, String> storage, Function<Object, Object> display,
        Consumer<Object> applier, UpdateMode mode, String reason) {
        put(map, key, category, type, defaultValue, mutable, liveApplied, sensitive, constraints, parser, storage, display,
            applier, mode, reason, reason);
    }

    private void put(Map<String, RuntimeSettingDefinition> map, String key, String category, SettingType type,
        Object defaultValue, boolean mutable, boolean liveApplied, boolean sensitive, Map<String, Object> constraints,
        Function<Object, Object> parser, Function<Object, String> storage, Function<Object, Object> display,
        Consumer<Object> applier, UpdateMode mode, String reason, String description) {
        map.put(key, new RuntimeSettingDefinition(key, category, type, defaultValue, mutable, liveApplied, sensitive,
            constraints, parser, storage, display, applier, mode, reason, label(key), description));
    }

    private String env(String key) {
        return environment == null ? "" : environment.getProperty(key, "");
    }

    private String envOrDefault(String key, String defaultValue) {
        return environment == null ? defaultValue : environment.getProperty(key, defaultValue);
    }

    private int envInt(String key, int defaultValue) {
        String value = envOrDefault(key, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Runtime setting " + key + " must be an integer", exception);
        }
    }

    private String label(String key) {
        String leaf = key.substring(key.lastIndexOf('.') + 1).replace('-', ' ');
        return leaf.substring(0, 1).toUpperCase(Locale.ROOT) + leaf.substring(1);
    }
}
