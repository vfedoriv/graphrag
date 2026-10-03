package io.github.vfedoriv.graphrag.observability;

import io.github.vfedoriv.graphrag.observability.configuration.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.ai.contracts.StartupModelMetadata;
import io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;
import io.github.vfedoriv.graphrag.ai.contracts.AiProfileAccess;
import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AiObservationService {

    public static final String OPERATION_CHAT = "chat";
    public static final String OPERATION_EMBEDDING = "embedding";

    public static final String WORKFLOW_DOCUMENT_PROCESSING = "document-processing";
    public static final String WORKFLOW_GRAPH_EXTRACTION = "graph-extraction";
    public static final String WORKFLOW_SCHEMA_GENERATION = "schema-generation";
    public static final String WORKFLOW_SCHEMA_DISCOVERY = "schema-discovery";
    public static final String WORKFLOW_SCHEMA_DRAFT_EVALUATION = "schema-draft-evaluation";
    public static final String WORKFLOW_SCHEMA_DRAFT_PUBLICATION = "schema-draft-publication";
    public static final String WORKFLOW_SCHEMA_REPROCESSING = "schema-reprocessing";
    public static final String WORKFLOW_CYPHER_GENERATION = "cypher-generation";
    public static final String WORKFLOW_QUERY = "query";
    public static final String WORKFLOW_ADVANCED_SEARCH = "advanced-search";

    private static final String MODEL_CALLS_METRIC = "graphrag.ai.model.calls";
    private static final String MODEL_FAILURES_METRIC = "graphrag.ai.model.failures";
    private static final String MODEL_LATENCY_METRIC = "graphrag.ai.model.latency";
    private static final String MODEL_TOKENS_METRIC = "graphrag.ai.model.tokens";
    static final String LANGFUSE_OBSERVATION_INPUT = "langfuse.observation.input";
    static final String LANGFUSE_OBSERVATION_OUTPUT = "langfuse.observation.output";
    static final String LANGFUSE_TRACE_INPUT = "langfuse.trace.input";
    static final String LANGFUSE_TRACE_OUTPUT = "langfuse.trace.output";
    static final String OTEL_INPUT_VALUE = "input.value";
    static final String OTEL_OUTPUT_VALUE = "output.value";
    static final String GEN_AI_PROMPT = "gen_ai.prompt";
    static final String GEN_AI_COMPLETION = "gen_ai.completion";
    static final String LANGFUSE_OBSERVATION_TYPE = "langfuse.observation.type";
    static final String LANGFUSE_OBSERVATION_MODEL_NAME = "langfuse.observation.model.name";
    static final String OTEL_MODEL = "model";
    static final String LANGFUSE_GENERATION_TYPE = "generation";

    private final AiObservabilityProperties properties;
    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;
    private final StartupModelMetadata appProperties;
    private final Environment environment;
    private final RuntimeSettingsAccess runtimeSettingsService;
    private final ObjectProvider<? extends AiProfileAccess> aiProfileServiceProvider;

    public AiObservationService(
        AiObservabilityProperties properties,
        ObservationRegistry observationRegistry,
        MeterRegistry meterRegistry,
        StartupModelMetadata appProperties,
        Environment environment,
        RuntimeSettingsAccess runtimeSettingsService,
        ObjectProvider<? extends AiProfileAccess> aiProfileServiceProvider
    ) {
        this.properties = properties;
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
        this.appProperties = appProperties;
        this.environment = environment;
        this.runtimeSettingsService = runtimeSettingsService;
        this.aiProfileServiceProvider = aiProfileServiceProvider;
    }

    public AiObservationScope startWorkflow(AiWorkflowContext context) {
        if (!settings().enabled()) {
            return AiObservationScope.noop();
        }
        Observation observation = startObservation("graphrag.ai.workflow", context.workflow(), context.schemaName());
        addHighCardinalityAttributes(observation, context.highCardinalityAttributes());
        return new AiObservationScope(observation, observation.openScope());
    }

    public AiModelCallObservation startChatModelCall(String workflow, String schemaName, Map<String, String> highCardinalityAttributes) {
        AiModelCallContext context = new AiModelCallContext(
            OPERATION_CHAT,
            workflow,
            providerProfile(true),
            modelName(true),
            schemaName,
            highCardinalityAttributes
        );
        return startModelCall(context);
    }

    public AiModelCallObservation startEmbeddingModelCall(String workflow, Map<String, String> highCardinalityAttributes) {
        AiModelCallContext context = new AiModelCallContext(
            OPERATION_EMBEDDING,
            workflow,
            providerProfile(false),
            modelName(false),
            null,
            highCardinalityAttributes
        );
        return startModelCall(context);
    }

    public Map<String, String> contentAttributes(String prefix, String value) {
        if (value == null) {
            return Map.of(prefix + ".length", "0");
        }
        if (settings().contentCaptureEnabled()) {
            return Map.of(
                prefix + ".length", String.valueOf(LogMetadata.length(value)),
                prefix + ".sha256", sha256(value),
                prefix + ".preview", truncate(LogMetadata.contentPreview(value)),
                prefix + ".content", truncate(value)
            );
        }
        return Map.of(
            prefix + ".length", String.valueOf(LogMetadata.length(value)),
            prefix + ".sha256", sha256(value),
            prefix + ".preview", truncate(LogMetadata.contentPreview(value))
        );
    }

    public Map<String, String> langfuseInputAttributes(String value) {
        String input = langfuseIoValue(value);
        return Map.of(
            LANGFUSE_OBSERVATION_INPUT, input,
            LANGFUSE_TRACE_INPUT, input,
            OTEL_INPUT_VALUE, input,
            GEN_AI_PROMPT, input
        );
    }

    public Map<String, String> langfuseOutputAttributes(String value) {
        String output = langfuseIoValue(value);
        return Map.of(
            LANGFUSE_OBSERVATION_OUTPUT, output,
            LANGFUSE_TRACE_OUTPUT, output,
            OTEL_OUTPUT_VALUE, output,
            GEN_AI_COMPLETION, output
        );
    }

    public boolean enabled() {
        return settings().enabled();
    }

    void recordModelCall(
        AiModelCallContext context,
        String status,
        String failureCategory,
        long elapsedNanos,
        AiTokenUsage tokenUsage
    ) {
        if (!settings().enabled()) {
            return;
        }
        Iterable<Tag> baseTags = modelTags(context, status, null);
        Counter.builder(MODEL_CALLS_METRIC)
            .description("AI model call attempts")
            .tags(baseTags)
            .register(meterRegistry)
            .increment();
        Timer.builder(MODEL_LATENCY_METRIC)
            .description("AI model call latency")
            .tags(baseTags)
            .register(meterRegistry)
            .record(elapsedNanos, TimeUnit.NANOSECONDS);
        if (AiObservationAttributes.STATUS_FAILURE.equals(status)) {
            Counter.builder(MODEL_FAILURES_METRIC)
                .description("AI model call failures")
                .tags(modelTags(context, status, failureCategory))
                .register(meterRegistry)
                .increment();
        }
        recordTokenUsage(context, tokenUsage);
    }

    public static String failureCategory(Throwable throwable) {
        if (throwable == null) {
            return AiObservationAttributes.UNKNOWN;
        }
        String className = throwable.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        String message = throwable.getMessage() == null ? "" : throwable.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (className.contains("timeout") || message.contains("timeout")) {
            return "timeout";
        }
        if (className.contains("json") || message.contains("parse") || message.contains("invalid")) {
            return "parse_error";
        }
        if (className.contains("validation") || message.contains("validation")) {
            return "validation_error";
        }
        if (className.contains("illegalstate") || message.contains("not configured") || message.contains("missing")) {
            return "configuration_error";
        }
        return "provider_error";
    }

    private AiModelCallObservation startModelCall(AiModelCallContext context) {
        if (!settings().enabled()) {
            return AiModelCallObservation.noop(this, context);
        }
        Observation parentObservation = observationRegistry.getCurrentObservation();
        Span parentSpan = Span.current();
        addTraceSpanInputOutputAttributes(parentSpan, context.highCardinalityAttributes());
        Observation observation = startObservation("graphrag.ai.model", context.workflow(), context.schemaName());
        observation.lowCardinalityKeyValue(AiObservationAttributes.OPERATION, stable(context.operation()));
        observation.lowCardinalityKeyValue(AiObservationAttributes.PROVIDER_PROFILE, stable(context.providerProfile()));
        observation.lowCardinalityKeyValue(AiObservationAttributes.MODEL_NAME, stable(context.modelName()));
        addLangfuseModelAttributes(observation, context);
        addHighCardinalityAttributes(observation, context.highCardinalityAttributes());
        addTraceInputOutputAttributes(parentObservation, context.highCardinalityAttributes());
        AiModelCallObservation modelObservation = new AiModelCallObservation(
            this,
            context,
            observation,
            observation.openScope(),
            parentObservation,
            parentSpan
        );
        addLangfuseModelSpanAttributes(Span.current(), context);
        addSpanInputOutputAttributes(Span.current(), context.highCardinalityAttributes());
        return modelObservation;
    }

    private Observation startObservation(String name, String workflow, String schemaName) {
        RuntimeSettingsAccess.AiObservationSettings settings = settings();
        Observation observation = settings.enabled()
            ? Observation.start(name, observationRegistry)
            : Observation.NOOP;
        observation.lowCardinalityKeyValue(AiObservationAttributes.WORKFLOW, stable(workflow));
        observation.lowCardinalityKeyValue(AiObservationAttributes.CONTENT_CAPTURE, String.valueOf(settings.contentCaptureEnabled()));
        if (settings.schemaNameTagEnabled() && schemaName != null && !schemaName.isBlank()) {
            observation.lowCardinalityKeyValue(AiObservationAttributes.SCHEMA_NAME, truncate(schemaName));
        }
        return observation;
    }

    private void addHighCardinalityAttributes(Observation observation, Map<String, String> attributes) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                observation.highCardinalityKeyValue(entry.getKey(), truncateAttribute(entry.getKey(), entry.getValue()));
            }
        }
    }

    private void addLangfuseModelAttributes(Observation observation, AiModelCallContext context) {
        String modelName = stable(context.modelName());
        observation.highCardinalityKeyValue(LANGFUSE_OBSERVATION_TYPE, LANGFUSE_GENERATION_TYPE);
        observation.highCardinalityKeyValue(LANGFUSE_OBSERVATION_MODEL_NAME, modelName);
        observation.highCardinalityKeyValue(OTEL_MODEL, modelName);
    }

    private void addLangfuseModelSpanAttributes(Span span, AiModelCallContext context) {
        if (span == null) {
            return;
        }
        String modelName = stable(context.modelName());
        span.setAttribute(LANGFUSE_OBSERVATION_TYPE, LANGFUSE_GENERATION_TYPE);
        span.setAttribute(LANGFUSE_OBSERVATION_MODEL_NAME, modelName);
        span.setAttribute(OTEL_MODEL, modelName);
    }

    void addTraceInputOutputAttributes(Observation observation, Map<String, String> attributes) {
        if (observation == null || observation.isNoop()) {
            return;
        }
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (isTraceInputOutputAttribute(entry.getKey()) && entry.getValue() != null) {
                observation.highCardinalityKeyValue(entry.getKey(), truncateInputOutput(entry.getValue()));
            }
        }
    }

    void addTraceSpanInputOutputAttributes(Span span, Map<String, String> attributes) {
        if (span == null) {
            return;
        }
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (isTraceInputOutputAttribute(entry.getKey()) && entry.getValue() != null) {
                span.setAttribute(entry.getKey(), truncateInputOutput(entry.getValue()));
            }
        }
    }

    void addSpanInputOutputAttributes(Span span, Map<String, String> attributes) {
        if (span == null) {
            return;
        }
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (isInputOutputAttribute(entry.getKey()) && entry.getValue() != null) {
                span.setAttribute(entry.getKey(), truncateInputOutput(entry.getValue()));
            }
        }
    }

    static boolean isInputOutputAttribute(String key) {
        return LANGFUSE_OBSERVATION_INPUT.equals(key)
            || LANGFUSE_OBSERVATION_OUTPUT.equals(key)
            || LANGFUSE_TRACE_INPUT.equals(key)
            || LANGFUSE_TRACE_OUTPUT.equals(key)
            || OTEL_INPUT_VALUE.equals(key)
            || OTEL_OUTPUT_VALUE.equals(key)
            || GEN_AI_PROMPT.equals(key)
            || GEN_AI_COMPLETION.equals(key);
    }

    static boolean isTraceInputOutputAttribute(String key) {
        return LANGFUSE_TRACE_INPUT.equals(key)
            || LANGFUSE_TRACE_OUTPUT.equals(key)
            || OTEL_INPUT_VALUE.equals(key)
            || OTEL_OUTPUT_VALUE.equals(key);
    }

    private Iterable<Tag> modelTags(AiModelCallContext context, String status, String failureCategory) {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of(AiObservationAttributes.OPERATION, stable(context.operation())));
        tags.add(Tag.of(AiObservationAttributes.WORKFLOW, stable(context.workflow())));
        tags.add(Tag.of(AiObservationAttributes.PROVIDER_PROFILE, stable(context.providerProfile())));
        tags.add(Tag.of(AiObservationAttributes.MODEL_NAME, stable(context.modelName())));
        tags.add(Tag.of(AiObservationAttributes.STATUS, stable(status)));
        RuntimeSettingsAccess.AiObservationSettings settings = settings();
        tags.add(Tag.of(AiObservationAttributes.CONTENT_CAPTURE, String.valueOf(settings.contentCaptureEnabled())));
        if (settings.schemaNameTagEnabled() && context.schemaName() != null && !context.schemaName().isBlank()) {
            tags.add(Tag.of(AiObservationAttributes.SCHEMA_NAME, truncate(context.schemaName())));
        }
        if (failureCategory != null && !failureCategory.isBlank()) {
            tags.add(Tag.of(AiObservationAttributes.FAILURE_CATEGORY, stable(failureCategory)));
        }
        return tags;
    }

    private void recordTokenUsage(AiModelCallContext context, AiTokenUsage tokenUsage) {
        if (tokenUsage == null || !tokenUsage.hasAny()) {
            return;
        }
        recordTokenCounter(context, "input", tokenUsage.inputTokens());
        recordTokenCounter(context, "output", tokenUsage.outputTokens());
        recordTokenCounter(context, "total", tokenUsage.totalTokens());
    }

    private void recordTokenCounter(AiModelCallContext context, String type, Long value) {
        if (value == null) {
            return;
        }
        Counter.builder(MODEL_TOKENS_METRIC)
            .description("AI model token usage")
            .tags(modelTags(context, AiObservationAttributes.STATUS_SUCCESS, null))
            .tag("token.type", type)
            .register(meterRegistry)
            .increment(value.doubleValue());
    }

    private String providerProfile(boolean chat) {
        ProfileFacts profile = activeProfile();
        if (profile != null) {
            return profile.getId();
        }
        if (environment == null) {
            return AiObservationAttributes.UNKNOWN;
        }
        String propertyName = chat ? "spring.ai.model.chat" : "spring.ai.model.embedding";
        return stable(environment.getProperty(propertyName, AiObservationAttributes.UNKNOWN));
    }

    private String modelName(boolean chat) {
        if (!settings().modelNameTagEnabled() || appProperties == null) {
            return AiObservationAttributes.UNKNOWN;
        }
        ProfileFacts profile = activeProfile();
        if (profile != null) {
            return chat ? profile.getChatModel() : profile.getEmbeddingModel();
        }
        return chat ? appProperties.chatModel() : appProperties.embeddingModel();
    }

    private ProfileFacts activeProfile() {
        String profileId = AiProfileContext.activeProfileId();
        AiProfileAccess aiProfileService = aiProfileServiceProvider.getIfAvailable();
        if (profileId == null || aiProfileService == null) {
            return null;
        }
        try {
            return aiProfileService.require(profileId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String stable(String value) {
        if (value == null || value.isBlank()) {
            return AiObservationAttributes.UNKNOWN;
        }
        return truncate(value);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        int maxLength = settings().maxAttributeLength();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String truncateAttribute(String key, String value) {
        if (isInputOutputAttribute(key)) {
            return truncateInputOutput(value);
        }
        return truncate(value);
    }

    private String truncateInputOutput(String value) {
        if (value == null) {
            return "";
        }
        int maxLength = settings().maxInputOutputLength();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String langfuseIoValue(String value) {
        int length = LogMetadata.length(value);
        String hash = value == null ? AiObservationAttributes.UNKNOWN : sha256(value);
        if (settings().inputOutputContentEnabled()) {
            return truncateInputOutput(value);
        }
        String preview = LogMetadata.contentPreview(value);
        return truncateInputOutput(preview + "\n\nmetadata: length=" + length + ", sha256=" + hash);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return AiObservationAttributes.UNKNOWN;
        }
    }

    private RuntimeSettingsAccess.AiObservationSettings settings() {
        if (runtimeSettingsService == null) {
            return new RuntimeSettingsAccess.AiObservationSettings(
                properties.enabled(),
                properties.contentCaptureEnabled(),
                properties.maxAttributeLength(),
                properties.inputOutputContentEnabled(),
                properties.maxInputOutputLength(),
                properties.modelNameTagEnabled(),
                properties.schemaNameTagEnabled()
            );
        }
        return runtimeSettingsService.aiObservation();
    }
}
