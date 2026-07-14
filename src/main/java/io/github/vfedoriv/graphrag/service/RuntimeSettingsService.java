package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.application.settings.RuntimeSettingCodecs;
import io.github.vfedoriv.graphrag.application.settings.RuntimeSettingDefinition;
import io.github.vfedoriv.graphrag.application.settings.RuntimeSettingDefinition.UpdateMode;
import io.github.vfedoriv.graphrag.application.settings.RuntimeSettingLifecycle;
import io.github.vfedoriv.graphrag.application.settings.RuntimeSettingLiveAppliers;
import io.github.vfedoriv.graphrag.application.settings.RuntimeSettingsCatalog;
import io.github.vfedoriv.graphrag.domain.RuntimeSettingOverrideNode;
import io.github.vfedoriv.graphrag.dto.RuntimeSettingResponse;
import io.github.vfedoriv.graphrag.query.QueryPolicy;
import java.time.Duration;
import io.github.vfedoriv.graphrag.dto.RuntimeSettingUpdateRequest;
import io.github.vfedoriv.graphrag.repository.RuntimeSettingOverrideRepository;
import io.github.vfedoriv.graphrag.infrastructure.persistence.RuntimeSettingOverrideStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuntimeSettingsService {

    private final RuntimeSettingOverrideStore overrideStore;
    private final Map<String, RuntimeSettingDefinition> definitions;
    private final RuntimeSettingLifecycle lifecycle;

    public RuntimeSettingsService(
        RuntimeSettingOverrideRepository repository,
        AppProperties appProperties,
        AiObservabilityProperties observabilityProperties,
        Environment environment
    ) {
        RuntimeSettingCodecs codecs = new RuntimeSettingCodecs();
        RuntimeSettingLiveAppliers liveAppliers = new RuntimeSettingLiveAppliers(
            LoggingSystem.get(RuntimeSettingsService.class.getClassLoader())
        );
        this.overrideStore = new RuntimeSettingOverrideStore(repository);
        this.definitions = new RuntimeSettingsCatalog(codecs, liveAppliers, environment)
            .build(appProperties, observabilityProperties, appProperties.storage().documentsRoot().toString());
        this.lifecycle = new RuntimeSettingLifecycle(overrideStore, definitions);
    }

    @Transactional
    public List<RuntimeSettingResponse> list() {
        ensureRestartRequiredOverridesLoaded();
        return definitions.values().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public RuntimeSettingResponse update(String key, Object value) {
        RuntimeSettingDefinition definition = requireDefinition(key);
        overrideStore.requireConfigured();
        ensureRestartRequiredOverridesLoaded();
        if (!definition.mutable()) {
            throw new IllegalArgumentException(nonMutableMessage(definition));
        }
        Object parsed = definition.parse(value);
        RuntimeSettingOverrideNode node = overrideStore.getOrCreate(key);
        node.setValue(definition.toStorage(parsed));
        node.setLifecycleState(lifecycle.state(definition, parsed));
        node.setUpdatedAt(Instant.now());
        overrideStore.save(node);
        definition.applyLive(parsed);
        return toResponse(definition);
    }

    @Transactional
    public List<RuntimeSettingResponse> update(List<RuntimeSettingUpdateRequest> updates) {
        overrideStore.requireConfigured();
        ensureRestartRequiredOverridesLoaded();
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("Bulk runtime setting update must include at least one setting");
        }

        List<ParsedSettingUpdate> parsedUpdates = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (RuntimeSettingUpdateRequest update : updates) {
            if (update == null) {
                throw new IllegalArgumentException("Bulk runtime setting update entries must not be null");
            }
            String key = update.key();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Bulk runtime setting update key must not be blank");
            }
            if (!seenKeys.add(key)) {
                throw new IllegalArgumentException("Bulk runtime setting update contains duplicate key: " + key);
            }
            RuntimeSettingDefinition definition = requireDefinition(key);
            if (!definition.mutable()) {
                throw new IllegalArgumentException(nonMutableMessage(definition));
            }
            Object parsed = definition.parse(update.value());
            parsedUpdates.add(new ParsedSettingUpdate(definition, parsed));
        }

        Instant updatedAt = Instant.now();
        for (ParsedSettingUpdate update : parsedUpdates) {
            RuntimeSettingOverrideNode node = overrideStore.getOrCreate(update.definition().key());
            node.setValue(update.definition().toStorage(update.value()));
            node.setLifecycleState(lifecycle.state(update.definition(), update.value()));
            node.setUpdatedAt(updatedAt);
            overrideStore.save(node);
        }
        for (ParsedSettingUpdate update : parsedUpdates) {
            update.definition().applyLive(update.value());
        }

        return parsedUpdates.stream()
            .map(ParsedSettingUpdate::definition)
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public RuntimeSettingResponse clear(String key) {
        RuntimeSettingDefinition definition = requireDefinition(key);
        overrideStore.requireConfigured();
        ensureRestartRequiredOverridesLoaded();
        if (!definition.mutable()) {
            throw new IllegalArgumentException(nonMutableMessage(definition));
        }
        overrideStore.delete(key);
        definition.applyLive(definition.defaultValue());
        return toResponse(definition);
    }

    @Transactional(readOnly = true)
    public Path documentStorageRoot() {
        RuntimeSettingDefinition definition = requireDefinition("app.storage.documents-root");
        ensureRestartRequiredOverridesLoaded();
        return Path.of(String.valueOf(lifecycle.activeParsedValue(definition)));
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
    public QueryPolicy queryPolicy() {
        QuerySettings settings = query();
        return new QueryPolicy(
            settings.maxRows(),
            Duration.ofSeconds(settings.timeoutSeconds()),
            settings.requireLimit(),
            settings.blockedKeywords()
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
    public DiscoverySettings discovery() {
        return new DiscoverySettings(
            integer("app.schema-discovery.max-sources"),
            integer("app.schema-discovery.max-source-bytes"),
            integer("app.schema-discovery.max-total-bytes"),
            integer("app.schema-discovery.max-source-characters"),
            integer("app.schema-discovery.max-total-characters"),
            integer("app.schema-discovery.chunk-characters"),
            integer("app.schema-discovery.max-chunks-per-source"),
            integer("app.schema-discovery.max-concurrency"),
            Duration.ofSeconds(integer("app.schema-discovery.source-timeout-seconds")),
            Duration.ofSeconds(integer("app.schema-discovery.request-timeout-seconds"))
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

    private RuntimeSettingResponse toResponse(RuntimeSettingDefinition definition) {
        RuntimeSettingOverrideNode override = overrideStore.find(definition.key()).orElse(null);
        boolean hasEffectiveOverride = override != null && definition.mutable();
        Object defaultValue = definition.displayValue(definition.defaultValue());
        Object parsedValue = hasEffectiveOverride ? definition.parse(override.getValue()) : definition.defaultValue();
        Object currentValue = hasEffectiveOverride ? definition.displayValue(parsedValue) : defaultValue;
        Object activeValue = definition.updateMode() == UpdateMode.RESTART_REQUIRED
            ? definition.displayValue(lifecycle.activeParsedValue(definition))
            : currentValue;
        String lifecycleState = hasEffectiveOverride ? lifecycle.state(definition, parsedValue) : "default";
        lifecycle.reconcile(override, lifecycleState);
        return new RuntimeSettingResponse(
            definition.key(),
            definition.category(),
            definition.type().name().toLowerCase(Locale.ROOT),
            currentValue,
            defaultValue,
            activeValue,
            hasEffectiveOverride ? "override" : "default",
            lifecycleState,
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

    @PostConstruct
    void loadPersistedRestartRequiredOverrides() {
        ensureRestartRequiredOverridesLoaded();
    }

    private void ensureRestartRequiredOverridesLoaded() {
        lifecycle.ensureLoaded();
    }

    private Object currentValue(RuntimeSettingDefinition definition) {
        if (!definition.mutable()) {
            return definition.displayValue(definition.defaultValue());
        }
        if (!overrideStore.configured()) {
            return definition.displayValue(definition.defaultValue());
        }
        return overrideStore.find(definition.key())
            .map(node -> definition.parse(node.getValue()))
            .map(definition::displayValue)
            .orElse(definition.displayValue(definition.defaultValue()));
    }

    private String nonMutableMessage(RuntimeSettingDefinition definition) {
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

    private RuntimeSettingDefinition requireDefinition(String key) {
        RuntimeSettingDefinition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Runtime setting is not allowlisted: " + key);
        }
        return definition;
    }

    private record ParsedSettingUpdate(RuntimeSettingDefinition definition, Object value) {
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

    public record DiscoverySettings(
        int maxSources,
        int maxSourceBytes,
        int maxTotalBytes,
        int maxSourceCharacters,
        int maxTotalCharacters,
        int chunkCharacters,
        int maxChunksPerSource,
        int maxConcurrency,
        Duration sourceTimeout,
        Duration requestTimeout
    ) {
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
