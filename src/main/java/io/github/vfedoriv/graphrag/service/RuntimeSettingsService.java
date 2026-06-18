package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.domain.RuntimeSettingOverrideNode;
import io.github.vfedoriv.graphrag.dto.RuntimeSettingResponse;
import io.github.vfedoriv.graphrag.repository.RuntimeSettingOverrideRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuntimeSettingsService {

    private final RuntimeSettingOverrideRepository repository;
    private final Environment environment;
    private final Map<String, SettingDefinition> definitions;

    public RuntimeSettingsService(
        RuntimeSettingOverrideRepository repository,
        AppProperties appProperties,
        AiObservabilityProperties observabilityProperties,
        Environment environment
    ) {
        this.repository = repository;
        this.environment = environment;
        this.definitions = buildDefinitions(appProperties, observabilityProperties);
    }

    @Transactional(readOnly = true)
    public List<RuntimeSettingResponse> list() {
        return definitions.values().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public RuntimeSettingResponse update(String key, Object value) {
        SettingDefinition definition = requireDefinition(key);
        if (repository == null) {
            throw new IllegalStateException("Runtime setting persistence is not configured");
        }
        if (!definition.mutable()) {
            throw new IllegalArgumentException(nonMutableMessage(definition));
        }
        Object parsed = definition.parse(value);
        RuntimeSettingOverrideNode node = new RuntimeSettingOverrideNode();
        node.setKey(key);
        node.setValue(definition.toStorage(parsed));
        node.setUpdatedAt(Instant.now());
        repository.save(node);
        return toResponse(definition);
    }

    @Transactional
    public RuntimeSettingResponse clear(String key) {
        SettingDefinition definition = requireDefinition(key);
        if (repository == null) {
            throw new IllegalStateException("Runtime setting persistence is not configured");
        }
        if (!definition.mutable()) {
            throw new IllegalArgumentException(nonMutableMessage(definition));
        }
        repository.deleteById(key);
        return toResponse(definition);
    }

    @Transactional(readOnly = true)
    public QuerySettings query() {
        return new QuerySettings(
            integer("app.query.max-rows"),
            integer("app.query.timeout-seconds"),
            bool("app.query.require-limit"),
            stringList("app.query.blocked-keywords"),
            integer("app.query.hybrid-search-default-top-k"),
            integer("app.query.hybrid-search-max-top-k"),
            integer("app.query.hybrid-search-candidate-multiplier"),
            integer("app.query.hybrid-search-max-candidates"),
            integer("app.query.hybrid-search-default-graph-depth"),
            integer("app.query.hybrid-search-max-graph-depth"),
            bool("app.query.hybrid-search-include-chunk-text")
        );
    }

    @Transactional(readOnly = true)
    public ChunkingSettings chunking() {
        return new ChunkingSettings(
            integer("app.chunking.max-tokens"),
            integer("app.chunking.overlap-tokens"),
            integer("app.chunking.max-characters")
        );
    }

    @Transactional(readOnly = true)
    public ExtractionSettings extraction() {
        return new ExtractionSettings(
            integer("app.extraction.max-entities-per-chunk"),
            integer("app.extraction.max-relationships-per-chunk"),
            integer("app.extraction.max-retries")
        );
    }

    @Transactional(readOnly = true)
    public AiObservationSettings aiObservation() {
        return new AiObservationSettings(
            bool("app.ai.observability.enabled"),
            bool("app.ai.observability.content-capture-enabled"),
            integer("app.ai.observability.max-attribute-length"),
            bool("app.ai.observability.input-output-content-enabled"),
            integer("app.ai.observability.max-input-output-length"),
            bool("app.ai.observability.model-name-tag-enabled"),
            bool("app.ai.observability.schema-name-tag-enabled")
        );
    }

    public boolean modelNameTagEnabled() {
        return bool("app.ai.observability.model-name-tag-enabled");
    }

    private RuntimeSettingResponse toResponse(SettingDefinition definition) {
        boolean hasOverride = repository != null && repository.findById(definition.key()).isPresent();
        return new RuntimeSettingResponse(
            definition.key(),
            definition.category(),
            definition.type().name().toLowerCase(Locale.ROOT),
            currentValue(definition),
            definition.displayValue(definition.defaultValue()),
            hasOverride ? "override" : "default",
            definition.mutable(),
            definition.liveApplied(),
            definition.sensitive(),
            definition.constraints(),
            definition.updateMode().apiValue(),
            definition.reason(),
            definition.label(),
            definition.description()
        );
    }

    private Object currentValue(SettingDefinition definition) {
        if (!definition.mutable()) {
            return definition.displayValue(definition.defaultValue());
        }
        if (repository == null) {
            return definition.displayValue(definition.defaultValue());
        }
        return repository.findById(definition.key())
            .map(node -> definition.parse(node.getValue()))
            .map(definition::displayValue)
            .orElse(definition.displayValue(definition.defaultValue()));
    }

    private String nonMutableMessage(SettingDefinition definition) {
        return "Runtime setting " + definition.key() + " cannot be changed through the runtime settings API"
            + " because it is " + definition.updateMode().apiValue()
            + (definition.reason() == null ? "" : ": " + definition.reason());
    }

    private int integer(String key) {
        return (Integer) currentValue(requireDefinition(key));
    }

    private boolean bool(String key) {
        return (Boolean) currentValue(requireDefinition(key));
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(String key) {
        return (List<String>) currentValue(requireDefinition(key));
    }

    private SettingDefinition requireDefinition(String key) {
        SettingDefinition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Runtime setting is not allowlisted: " + key);
        }
        return definition;
    }

    private Map<String, SettingDefinition> buildDefinitions(
        AppProperties appProperties,
        AiObservabilityProperties observabilityProperties
    ) {
        Map<String, SettingDefinition> map = new LinkedHashMap<>();
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
        addReadOnlyString(map, "logging.level.root", "logging", env("logging.level.root"), UpdateMode.READ_ONLY, false, "Root logging level is startup-bound in this catalog.");
        addReadOnlyString(map, "spring.ai.model.chat", "spring-ai", env("spring.ai.model.chat"), UpdateMode.RESTART_REQUIRED, false, "Spring AI model bootstrap is bound during startup.");
        addReadOnlyString(map, "spring.ai.model.embedding", "spring-ai", env("spring.ai.model.embedding"), UpdateMode.RESTART_REQUIRED, false, "Spring AI model bootstrap is bound during startup.");
        addReadOnlyString(map, "spring.autoconfigure.exclude", "spring", env("spring.autoconfigure.exclude"), UpdateMode.RESTART_REQUIRED, false, "Auto-configuration exclusions are evaluated during startup.");
        addReadOnlyString(map, "spring.neo4j.uri", "neo4j", env("spring.neo4j.uri"), UpdateMode.RESTART_REQUIRED, false, "Neo4j connectivity is managed by startup-created infrastructure.");
        addReadOnlyString(map, "spring.neo4j.authentication.username", "neo4j", env("spring.neo4j.authentication.username"), UpdateMode.RESTART_REQUIRED, false, "Neo4j authentication is managed by startup-created infrastructure.");
        addReadOnlyString(map, "spring.neo4j.authentication.password", "neo4j", env("spring.neo4j.authentication.password"), UpdateMode.SENSITIVE_READ_ONLY, true, "Neo4j credentials are sensitive and startup-bound.");
        addReadOnlyString(map, "app.neo4j.database", "neo4j", appProperties.neo4j().database(), UpdateMode.RESTART_REQUIRED, false, "Neo4j database selection is bound to repository infrastructure.");
        addReadOnlyString(map, "app.storage.documents-root", "storage", appProperties.storage().documentsRoot().toString(), UpdateMode.RESTART_REQUIRED, false, "Document storage root is resolved by storage services at startup.");
        addReadOnlyString(map, "spring.servlet.multipart.max-file-size", "multipart", env("spring.servlet.multipart.max-file-size"), UpdateMode.RESTART_REQUIRED, false, "Multipart limits are applied by web infrastructure.");
        addReadOnlyString(map, "spring.servlet.multipart.max-request-size", "multipart", env("spring.servlet.multipart.max-request-size"), UpdateMode.RESTART_REQUIRED, false, "Multipart limits are applied by web infrastructure.");
        addReadOnlyString(map, "management.endpoints.web.exposure.include", "actuator", env("management.endpoints.web.exposure.include"), UpdateMode.RESTART_REQUIRED, false, "Actuator exposure is configured at startup.");
        addReadOnlyString(map, "management.endpoint.health.probes.enabled", "actuator", env("management.endpoint.health.probes.enabled"), UpdateMode.RESTART_REQUIRED, false, "Health probe configuration is bound during startup.");
        addReadOnlyString(map, "management.tracing.enabled", "tracing", env("management.tracing.enabled"), UpdateMode.RESTART_REQUIRED, false, "Tracing infrastructure is configured at startup.");
        addReadOnlyString(map, "management.tracing.export.otlp.enabled", "tracing", env("management.tracing.export.otlp.enabled"), UpdateMode.RESTART_REQUIRED, false, "OTLP exporter infrastructure is configured at startup.");
        addReadOnlyString(map, "management.tracing.sampling.probability", "tracing", env("management.tracing.sampling.probability"), UpdateMode.RESTART_REQUIRED, false, "Tracing sampling is configured at startup.");
        addReadOnlyString(map, "management.opentelemetry.tracing.export.otlp.endpoint", "opentelemetry", env("management.opentelemetry.tracing.export.otlp.endpoint"), UpdateMode.RESTART_REQUIRED, false, "OTLP exporter endpoint is configured at startup.");
        addReadOnlyString(map, "management.opentelemetry.tracing.export.otlp.headers.Authorization", "opentelemetry", env("management.opentelemetry.tracing.export.otlp.headers.Authorization"), UpdateMode.SENSITIVE_READ_ONLY, true, "OTLP authorization headers are sensitive and startup-bound.");
        addReadOnlyString(map, "management.opentelemetry.tracing.export.otlp.headers.x-langfuse-ingestion-version", "opentelemetry", env("management.opentelemetry.tracing.export.otlp.headers.x-langfuse-ingestion-version"), UpdateMode.RESTART_REQUIRED, false, "OTLP exporter headers are configured at startup.");
        addReadOnlyString(map, "app.model.base-url", "ai-provider", appProperties.model().baseUrl(), UpdateMode.PROFILE_MANAGED, false, "Use AI profile management to change provider behavior.");
        addReadOnlyString(map, "app.model.api-key", "ai-provider", appProperties.model().apiKey(), UpdateMode.SENSITIVE_READ_ONLY, true, "Use AI profile management to change provider secrets.");
        addReadOnlyString(map, "app.model.embedding-model", "ai-provider", appProperties.model().embeddingModel(), UpdateMode.PROFILE_MANAGED, false, "Use AI profile management to change provider behavior.");
        addReadOnlyString(map, "app.model.embedding-dimensions", "ai-provider", Integer.toString(appProperties.model().embeddingDimensions()), UpdateMode.PROFILE_MANAGED, false, "Use AI profile management to change provider behavior.");
        addReadOnlyString(map, "app.model.chat-model", "ai-provider", appProperties.model().chatModel(), UpdateMode.PROFILE_MANAGED, false, "Use AI profile management to change provider behavior.");
        addReadOnlyString(map, "spring.ai.openai.base-url", "spring-ai-openai", env("spring.ai.openai.base-url"), UpdateMode.PROFILE_MANAGED, false, "Derived from AI provider defaults; use AI profile management for workflow changes.");
        addReadOnlyString(map, "spring.ai.openai.api-key", "spring-ai-openai", env("spring.ai.openai.api-key"), UpdateMode.SENSITIVE_READ_ONLY, true, "Derived API key is sensitive; use AI profile management for workflow changes.");
        addReadOnlyString(map, "spring.ai.openai.embedding.options.model", "spring-ai-openai", env("spring.ai.openai.embedding.options.model"), UpdateMode.PROFILE_MANAGED, false, "Derived from AI provider defaults; use AI profile management for workflow changes.");
        addReadOnlyString(map, "spring.ai.openai.chat.options.model", "spring-ai-openai", env("spring.ai.openai.chat.options.model"), UpdateMode.PROFILE_MANAGED, false, "Derived from AI provider defaults; use AI profile management for workflow changes.");
        addReadOnlyString(map, "spring.ai.openai.timeout", "spring-ai-openai", env("spring.ai.openai.timeout"), UpdateMode.RESTART_REQUIRED, false, "Spring AI client timeout is configured at startup.");
        addReadOnlyString(map, "spring.ai.openai.chat.timeout", "spring-ai-openai", env("spring.ai.openai.chat.timeout"), UpdateMode.RESTART_REQUIRED, false, "Spring AI client timeout is configured at startup.");
        addReadOnlyString(map, "spring.ai.openai.embedding.timeout", "spring-ai-openai", env("spring.ai.openai.embedding.timeout"), UpdateMode.RESTART_REQUIRED, false, "Spring AI client timeout is configured at startup.");
        addReadOnlyString(map, "spring.ai.chat.observations.log-prompt", "spring-ai-observability", env("spring.ai.chat.observations.log-prompt"), UpdateMode.RESTART_REQUIRED, false, "Spring AI observation logging is configured at startup.");
        addReadOnlyString(map, "spring.ai.chat.observations.log-completion", "spring-ai-observability", env("spring.ai.chat.observations.log-completion"), UpdateMode.RESTART_REQUIRED, false, "Spring AI observation logging is configured at startup.");
        return Map.copyOf(map);
    }

    private void addInt(Map<String, SettingDefinition> map, String key, String category, int defaultValue, int min) {
        map.put(key, new SettingDefinition(
            key,
            category,
            SettingType.INTEGER,
            defaultValue,
            true,
            true,
            false,
            Map.of("min", min),
            value -> parseInteger(key, value, min),
            Objects::toString,
            Function.identity(),
            UpdateMode.LIVE,
            null,
            label(key),
            null
        ));
    }

    private void addBool(Map<String, SettingDefinition> map, String key, String category, boolean defaultValue) {
        map.put(key, new SettingDefinition(
            key,
            category,
            SettingType.BOOLEAN,
            defaultValue,
            true,
            true,
            false,
            Map.of(),
            value -> parseBoolean(key, value),
            Objects::toString,
            Function.identity(),
            UpdateMode.LIVE,
            null,
            label(key),
            null
        ));
    }

    private void addStringList(Map<String, SettingDefinition> map, String key, String category, List<String> defaultValue) {
        map.put(key, new SettingDefinition(
            key,
            category,
            SettingType.STRING_LIST,
            List.copyOf(defaultValue),
            true,
            true,
            false,
            Map.of("minItems", 1),
            value -> parseStringList(key, value),
            value -> String.join(",", castStringList(value)),
            Function.identity(),
            UpdateMode.LIVE,
            null,
            label(key),
            null
        ));
    }

    private void addReadOnlyString(
        Map<String, SettingDefinition> map,
        String key,
        String category,
        String defaultValue,
        UpdateMode updateMode,
        boolean sensitive,
        String reason
    ) {
        map.put(key, new SettingDefinition(
            key,
            category,
            SettingType.STRING,
            defaultValue == null ? "" : defaultValue,
            false,
            false,
            sensitive,
            Map.of(),
            value -> String.valueOf(value),
            Objects::toString,
            sensitive ? this::maskedValue : Function.identity(),
            updateMode,
            reason,
            label(key),
            reason
        ));
    }

    private String env(String key) {
        if (environment == null) {
            return "";
        }
        return environment.getProperty(key, "");
    }

    private Object maskedValue(Object value) {
        String text = String.valueOf(value == null ? "" : value);
        return Map.of("configured", !text.isBlank(), "masked", true);
    }

    private String label(String key) {
        String leaf = key.substring(key.lastIndexOf('.') + 1).replace('-', ' ');
        return leaf.substring(0, 1).toUpperCase(Locale.ROOT) + leaf.substring(1);
    }

    private Integer parseInteger(String key, Object value, int min) {
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(key + " must be an integer", ex);
            }
        }
        if (parsed < min) {
            throw new IllegalArgumentException(key + " must be greater than or equal to " + min);
        }
        return parsed;
    }

    private Boolean parseBoolean(String key, Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException(key + " must be a boolean");
    }

    private List<String> parseStringList(String key, Object value) {
        List<String> items = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addListItem(items, item);
            }
        } else {
            for (String item : String.valueOf(value).split(",")) {
                addListItem(items, item);
            }
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException(key + " must include at least one value");
        }
        return List.copyOf(items);
    }

    private void addListItem(List<String> items, Object item) {
        String text = String.valueOf(item).trim();
        if (!text.isBlank()) {
            items.add(text);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        return (List<String>) value;
    }

    private enum SettingType {
        INTEGER,
        BOOLEAN,
        STRING,
        STRING_LIST
    }

    private enum UpdateMode {
        LIVE("live"),
        RESTART_REQUIRED("restart-required"),
        PROFILE_MANAGED("profile-managed"),
        READ_ONLY("read-only"),
        SENSITIVE_READ_ONLY("sensitive-read-only");

        private final String apiValue;

        UpdateMode(String apiValue) {
            this.apiValue = apiValue;
        }

        String apiValue() {
            return apiValue;
        }
    }

    private record SettingDefinition(
        String key,
        String category,
        SettingType type,
        Object defaultValue,
        boolean mutable,
        boolean liveApplied,
        boolean sensitive,
        Map<String, Object> constraints,
        Function<Object, Object> parser,
        Function<Object, String> storageMapper,
        Function<Object, Object> displayMapper,
        UpdateMode updateMode,
        String reason,
        String label,
        String description
    ) {
        Object parse(Object value) {
            return parser.apply(value);
        }

        String toStorage(Object value) {
            return storageMapper.apply(value);
        }

        Object displayValue(Object value) {
            return displayMapper.apply(value);
        }
    }

    public record QuerySettings(
        int maxRows,
        int timeoutSeconds,
        boolean requireLimit,
        List<String> blockedKeywords,
        int hybridSearchDefaultTopK,
        int hybridSearchMaxTopK,
        int hybridSearchCandidateMultiplier,
        int hybridSearchMaxCandidates,
        int hybridSearchDefaultGraphDepth,
        int hybridSearchMaxGraphDepth,
        boolean hybridSearchIncludeChunkText
    ) {
    }

    public record ChunkingSettings(int maxTokens, int overlapTokens, int maxCharacters) {
    }

    public record ExtractionSettings(int maxEntitiesPerChunk, int maxRelationshipsPerChunk, int maxRetries) {
    }

    public record AiObservationSettings(
        boolean enabled,
        boolean contentCaptureEnabled,
        int maxAttributeLength,
        boolean inputOutputContentEnabled,
        int maxInputOutputLength,
        boolean modelNameTagEnabled,
        boolean schemaNameTagEnabled
    ) {
    }
}
