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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuntimeSettingsService {

    private final RuntimeSettingOverrideRepository repository;
    private final Map<String, SettingDefinition> definitions;

    public RuntimeSettingsService(
        RuntimeSettingOverrideRepository repository,
        AppProperties appProperties,
        AiObservabilityProperties observabilityProperties
    ) {
        this.repository = repository;
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
            throw new IllegalArgumentException("Runtime setting is not mutable: " + key);
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
            definition.defaultValue(),
            hasOverride ? "override" : "default",
            definition.mutable(),
            definition.liveApplied(),
            definition.sensitive(),
            definition.constraints()
        );
    }

    private Object currentValue(SettingDefinition definition) {
        if (repository == null) {
            return definition.defaultValue();
        }
        return repository.findById(definition.key())
            .map(node -> definition.parse(node.getValue()))
            .orElse(definition.defaultValue());
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
            Objects::toString
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
            Objects::toString
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
            value -> String.join(",", castStringList(value))
        ));
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
        STRING_LIST
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
        Function<Object, String> storageMapper
    ) {
        Object parse(Object value) {
            return parser.apply(value);
        }

        String toStorage(Object value) {
            return storageMapper.apply(value);
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
