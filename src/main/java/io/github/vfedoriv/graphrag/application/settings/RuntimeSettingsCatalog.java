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
        addInt(map, "app.chunking.max-tokens", "chunking", appProperties.chunking().maxTokens(), 1);
        addInt(map, "app.chunking.overlap-tokens", "chunking", appProperties.chunking().overlapTokens(), 0);
        addInt(map, "app.chunking.max-characters", "chunking", appProperties.chunking().maxCharacters(), 1);
        addInt(map, "app.extraction.max-entities-per-chunk", "extraction", appProperties.extraction().maxEntitiesPerChunk(), 1);
        addInt(map, "app.extraction.max-relationships-per-chunk", "extraction", appProperties.extraction().maxRelationshipsPerChunk(), 1);
        addInt(map, "app.extraction.max-retries", "extraction", appProperties.extraction().maxRetries(), 0);
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

    private String label(String key) {
        String leaf = key.substring(key.lastIndexOf('.') + 1).replace('-', ' ');
        return leaf.substring(0, 1).toUpperCase(Locale.ROOT) + leaf.substring(1);
    }
}
