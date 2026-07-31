package io.github.vfedoriv.graphrag.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.config.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.service.EmptyObjectProvider;
import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AiObservationServiceTest {

    @Test
    void disabledModeDoesNotRecordModelMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiObservationService service = service(false, false, meterRegistry);

        try (AiModelCallObservation observation = service.startChatModelCall(
            AiObservationService.WORKFLOW_CYPHER_GENERATION,
            "contracts",
            Map.of("document.id", "doc-1")
        )) {
            observation.success(new AiTokenUsage(10L, 5L, 15L));
        }

        assertThat(meterRegistry.getMeters()).isEmpty();
    }

    @Test
    void contentAttributesUseSanitizedMetadataWhenCaptureDisabled() {
        AiObservationService service = service(true, false, new SimpleMeterRegistry());

        Map<String, String> attributes = service.contentAttributes("ai.prompt", "hello world");

        assertThat(attributes).containsKeys("ai.prompt.length", "ai.prompt.sha256", "ai.prompt.preview");
        assertThat(attributes).doesNotContainKey("ai.prompt.content");
    }

    @Test
    void contentAttributesIncludeContentWhenCaptureEnabled() {
        AiObservationService service = service(true, true, new SimpleMeterRegistry());

        Map<String, String> attributes = service.contentAttributes("ai.prompt", "hello world");

        assertThat(attributes).containsEntry("ai.prompt.content", "hello world");
    }

    @Test
    void langfuseInputOutputAttributesUseRecognizedKeysAndFullContentByDefault() {
        AiObservationService service = service(true, false, new SimpleMeterRegistry());

        Map<String, String> input = service.langfuseInputAttributes("hello secret world");
        Map<String, String> output = service.langfuseOutputAttributes("model response");

        assertThat(input).containsKeys("langfuse.observation.input", "langfuse.trace.input");
        assertThat(output).containsKeys("langfuse.observation.output", "langfuse.trace.output");
        assertThat(input.get("langfuse.observation.input")).isEqualTo("hello secret world");
        assertThat(output.get("langfuse.observation.output")).isEqualTo("model response");
    }

    @Test
    void langfuseInputOutputAttributesUseSanitizedValuesWhenInputOutputContentDisabled() {
        AiObservationService service = service(true, false, false, 128, new SimpleMeterRegistry());

        Map<String, String> input = service.langfuseInputAttributes("hello secret world");
        Map<String, String> output = service.langfuseOutputAttributes("model response");

        assertThat(input.get("langfuse.observation.input")).contains("hello secret world", "metadata: length=", "sha256=");
        assertThat(output.get("langfuse.observation.output")).contains("model response", "metadata: length=", "sha256=");
    }

    @Test
    void langfuseInputOutputAttributesUseDedicatedMaxLength() {
        AiObservationService service = service(true, false, true, 4, new SimpleMeterRegistry());

        Map<String, String> input = service.langfuseInputAttributes("123456789");

        assertThat(input.get("langfuse.observation.input")).isEqualTo("1234");
    }

    @Test
    void langfuseInputOutputAttributesCanExceedGenericAttributeMaxLength() {
        AiObservationService service = service(
            true,
            false,
            ObservationRegistry.create(),
            true,
            32,
            128,
            new SimpleMeterRegistry()
        );

        Map<String, String> input = service.langfuseInputAttributes("123456789");

        assertThat(input.get("langfuse.observation.input")).isEqualTo("123456789");
    }

    @Test
    void modelCallRecordsObservationInputOutputAndParentTraceInputOutput() {
        CapturingObservationHandler handler = new CapturingObservationHandler();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(handler);
        AiObservationService service = service(true, false, observationRegistry, new SimpleMeterRegistry());

        try (AiObservationScope workflow = service.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_CYPHER_GENERATION,
            "contracts",
            Map.of("workflow.id", "wf-1")
        ))) {
            Map<String, String> inputAttributes = service.langfuseInputAttributes("prompt text");
            try (AiModelCallObservation observation = service.startChatModelCall(
                AiObservationService.WORKFLOW_CYPHER_GENERATION,
                "contracts",
                inputAttributes
            )) {
                observation.highCardinalityAttributes(service.langfuseOutputAttributes("model text"));
                observation.success(AiTokenUsage.none());
            }
            workflow.success();
        }

        CapturedObservation modelObservation = handler.singleObservationNamed("graphrag.ai.model");
        CapturedObservation workflowObservation = handler.singleObservationNamed("graphrag.ai.workflow");
        assertThat(modelObservation.highCardinalityAttributes())
            .containsEntry("langfuse.observation.type", "generation")
            .containsEntry("langfuse.observation.model.name", "gpt-5-mini")
            .containsEntry("model", "gpt-5-mini")
            .containsEntry("langfuse.observation.input", "prompt text")
            .containsEntry("langfuse.observation.output", "model text")
            .containsEntry("langfuse.trace.input", "prompt text")
            .containsEntry("langfuse.trace.output", "model text");
        assertThat(workflowObservation.highCardinalityAttributes())
            .containsEntry("langfuse.trace.input", "prompt text")
            .containsEntry("langfuse.trace.output", "model text")
            .containsEntry("input.value", "prompt text")
            .containsEntry("output.value", "model text")
            .doesNotContainKeys("langfuse.observation.input", "langfuse.observation.output");
    }

    @Test
    void successfulModelCallRecordsCountersTimerAndTokenUsage() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiObservationService service = service(true, false, meterRegistry);

        try (AiModelCallObservation observation = service.startChatModelCall(
            AiObservationService.WORKFLOW_CYPHER_GENERATION,
            "contracts",
            Map.of("document.id", "doc-1")
        )) {
            observation.success(new AiTokenUsage(10L, 5L, 15L));
        }

        assertThat(meterRegistry.get("graphrag.ai.model.calls").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("graphrag.ai.model.latency").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("graphrag.ai.model.tokens").tag("token.type", "input").counter().count()).isEqualTo(10.0);
        assertThat(meterRegistry.get("graphrag.ai.model.tokens").tag("token.type", "output").counter().count()).isEqualTo(5.0);
        assertThat(meterRegistry.get("graphrag.ai.model.tokens").tag("token.type", "total").counter().count()).isEqualTo(15.0);
        assertThat(allTagKeys(meterRegistry)).doesNotContain("document.id");
    }

    @Test
    void failedModelCallRecordsFailureCategory() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiObservationService service = service(true, false, meterRegistry);

        try (AiModelCallObservation observation = service.startChatModelCall(
            AiObservationService.WORKFLOW_GRAPH_EXTRACTION,
            "contracts",
            Map.of()
        )) {
            observation.error(new IllegalStateException("model is not configured"));
        }

        assertThat(meterRegistry.get("graphrag.ai.model.calls").tag(AiObservationAttributes.STATUS, "failure").counter().count())
            .isEqualTo(1.0);
        assertThat(meterRegistry.get("graphrag.ai.model.failures").tag(AiObservationAttributes.FAILURE_CATEGORY, "configuration_error").counter().count())
            .isEqualTo(1.0);
    }

    @Test
    void tokenUsageExtractsSpringAiStyleUsageMetadata() {
        AiTokenUsage usage = AiTokenUsage.fromResponse(new Response(new Metadata(new Usage(11L, 7L, 18L))));

        assertThat(usage.inputTokens()).isEqualTo(11L);
        assertThat(usage.outputTokens()).isEqualTo(7L);
        assertThat(usage.totalTokens()).isEqualTo(18L);
    }

    private AiObservationService service(boolean enabled, boolean contentCaptureEnabled, SimpleMeterRegistry meterRegistry) {
        return service(enabled, contentCaptureEnabled, ObservationRegistry.create(), true, 128, meterRegistry);
    }

    private AiObservationService service(
        boolean enabled,
        boolean contentCaptureEnabled,
        boolean inputOutputContentEnabled,
        int maxInputOutputLength,
        SimpleMeterRegistry meterRegistry
    ) {
        return service(enabled, contentCaptureEnabled, ObservationRegistry.create(), inputOutputContentEnabled, 128, maxInputOutputLength, meterRegistry);
    }

    private AiObservationService service(
        boolean enabled,
        boolean contentCaptureEnabled,
        ObservationRegistry observationRegistry,
        SimpleMeterRegistry meterRegistry
    ) {
        return service(enabled, contentCaptureEnabled, observationRegistry, true, 128, meterRegistry);
    }

    private AiObservationService service(
        boolean enabled,
        boolean contentCaptureEnabled,
        ObservationRegistry observationRegistry,
        boolean inputOutputContentEnabled,
        int maxInputOutputLength,
        SimpleMeterRegistry meterRegistry
    ) {
        return service(enabled, contentCaptureEnabled, observationRegistry, inputOutputContentEnabled, 128, maxInputOutputLength, meterRegistry);
    }

    private AiObservationService service(
        boolean enabled,
        boolean contentCaptureEnabled,
        ObservationRegistry observationRegistry,
        boolean inputOutputContentEnabled,
        int maxAttributeLength,
        int maxInputOutputLength,
        SimpleMeterRegistry meterRegistry
    ) {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.ai.model.chat", "openai")
            .withProperty("spring.ai.model.embedding", "openai");
        AiObservabilityProperties properties = new AiObservabilityProperties(
                enabled,
                contentCaptureEnabled,
                maxAttributeLength,
                inputOutputContentEnabled,
                maxInputOutputLength,
                true,
                true
            );
        AppProperties appProperties = appProperties();
        return new AiObservationService(
            properties,
            observationRegistry,
            meterRegistry,
            appProperties,
            environment,
            TestRuntimeSettings.from(appProperties, properties),
            new EmptyObjectProvider<>()
        );
    }

    private AppProperties appProperties() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 80, 4000),
            new AppProperties.Query(200, 15, true, List.of("CREATE")),
            new AppProperties.Extraction(40, 80, 2)
        );
    }

    private List<String> allTagKeys(SimpleMeterRegistry meterRegistry) {
        return meterRegistry.getMeters().stream()
            .flatMap(meter -> meter.getId().getTags().stream())
            .map(Tag::getKey)
            .distinct()
            .toList();
    }

    private static final class CapturingObservationHandler implements ObservationHandler<Observation.Context> {

        private final List<CapturedObservation> observations = new ArrayList<>();

        @Override
        public void onStop(Observation.Context context) {
            Map<String, String> highCardinalityAttributes = new HashMap<>();
            for (KeyValue keyValue : context.getHighCardinalityKeyValues()) {
                highCardinalityAttributes.put(keyValue.getKey(), keyValue.getValue());
            }
            observations.add(new CapturedObservation(context.getName(), highCardinalityAttributes));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        private CapturedObservation singleObservationNamed(String name) {
            List<CapturedObservation> matches = observations.stream()
                .filter(observation -> name.equals(observation.name()))
                .toList();
            assertThat(matches).hasSize(1);
            return matches.getFirst();
        }
    }

    private record CapturedObservation(String name, Map<String, String> highCardinalityAttributes) {
    }

    private record Response(Metadata metadata) {
        public Metadata getMetadata() {
            return metadata;
        }
    }

    private record Metadata(Usage usage) {
        public Usage getUsage() {
            return usage;
        }
    }

    private record Usage(Long promptTokens, Long completionTokens, Long totalTokens) {
        public Long getPromptTokens() {
            return promptTokens;
        }

        public Long getCompletionTokens() {
            return completionTokens;
        }

        public Long getTotalTokens() {
            return totalTokens;
        }
    }
}
