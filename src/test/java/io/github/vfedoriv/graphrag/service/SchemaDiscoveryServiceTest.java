package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts;
import io.github.vfedoriv.graphrag.schemas.discovery.application.SchemaDiscoveryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryAggregator;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.ResponseStatus;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.SourceStatus;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.SourceType;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoverySourceAnalyzer;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoverySourcePreparer;
import io.github.vfedoriv.graphrag.schemas.discovery.PreparedDiscoverySource;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureClassifier;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryResponse;
import io.github.vfedoriv.graphrag.error.SchemaDiscoveryFailedException;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SchemaDiscoveryServiceTest {

    private final DiscoverySourcePreparer preparer = mock(DiscoverySourcePreparer.class);
    private final DiscoverySourceAnalyzer analyzer = mock(DiscoverySourceAnalyzer.class);
    private final DiscoveryAggregator aggregator = mock(DiscoveryAggregator.class);
    private final RuntimeSettingsService settings = mock(RuntimeSettingsService.class);
    private final KnowledgeBaseService knowledgeBases = mock(KnowledgeBaseService.class);
    private final AiObservationService observations = TestAiObservationService.noop();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private SchemaDiscoveryService service;

    @BeforeEach
    void setUp() {
        when(settings.discovery()).thenReturn(new RuntimeSettingsService.DiscoverySettings(
            4, 1000, 4000, 1000, 4000, 1000, 2, 2, Duration.ofSeconds(1), Duration.ofSeconds(2)));
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");
        profile.setRevision(7);
        when(knowledgeBases.activeAiProfile("kb")).thenReturn(profile);
        service = new SchemaDiscoveryService(
            preparer, analyzer, aggregator, settings, knowledgeBases, observations, new SourceFailureClassifier());
    }

    @Test
    void neverCallsModelWhenInputPreparationFails() {
        SchemaDiscoveryRequest request = request();
        when(preparer.prepare("kb", request, List.of()))
            .thenThrow(new IllegalArgumentException("Discovery source exceeds byte limit 100"));

        assertThatThrownBy(() -> service.discover("kb", request, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Discovery source exceeds byte limit 100");
        verifyNoInteractions(analyzer);
    }

    @Test
    void returnsPartialInInputOrderAndPropagatesActiveProfileToWorkers() {
        PreparedDiscoverySource first = source("source-1");
        PreparedDiscoverySource second = source("source-2");
        SchemaDiscoveryRequest request = request();
        when(preparer.prepare("kb", request, List.of())).thenReturn(List.of(first, second));
        when(analyzer.analyze(any(), eq(request), any())).thenAnswer(invocation -> {
            PreparedDiscoverySource source = invocation.getArgument(0);
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("profile-1");
            if (source.sourceId().equals("source-2")) {
                throw new IllegalStateException("provider unavailable");
            }
            return new DiscoverySourceAnalyzer.SourceAnalysis(source, List.of(), List.of());
        });
        DiscoveryAggregator.AggregateResult aggregate = new DiscoveryAggregator.AggregateResult(
            List.of(), List.of(), List.of(), objectMapper.createObjectNode());
        when(aggregator.aggregate(any(), eq(request))).thenReturn(aggregate);

        SchemaDiscoveryResponse response = service.discover("kb", request, List.of());

        assertThat(response.status()).isEqualTo(ResponseStatus.PARTIAL);
        assertThat(response.sourceOutcomes()).extracting(outcome -> outcome.sourceId())
            .containsExactly("source-1", "source-2");
        assertThat(response.sourceOutcomes()).extracting(outcome -> outcome.status())
            .containsExactly(SourceStatus.SUCCEEDED, SourceStatus.FAILED);
        assertThat(response.reproducibility().aiProfileId()).isEqualTo("profile-1");
        assertThat(response.reproducibility().aiProfileRevision()).isEqualTo(7);
    }

    @Test
    void rejectsApparentlySuccessfulEmptyResultWhenAllSourcesFail() {
        PreparedDiscoverySource source = source("source-1");
        SchemaDiscoveryRequest request = request();
        when(preparer.prepare("kb", request, List.of())).thenReturn(List.of(source));
        when(analyzer.analyze(eq(source), eq(request), any())).thenThrow(new IllegalArgumentException("invalid candidates"));

        assertThatThrownBy(() -> service.discover("kb", request, List.of()))
            .isInstanceOf(SchemaDiscoveryFailedException.class)
            .hasMessageContaining("failed for all 1 sources");
    }

    @Test
    void classifiesTimeoutAndOverloadWhileKeepingSuccessfulSources() {
        PreparedDiscoverySource success = source("source-success");
        PreparedDiscoverySource timeout = source("source-timeout");
        PreparedDiscoverySource overloaded = source("source-overloaded");
        SchemaDiscoveryRequest request = request();
        when(settings.discovery()).thenReturn(new RuntimeSettingsService.DiscoverySettings(
            4, 1000, 4000, 1000, 4000, 1000, 2, 3, Duration.ofMillis(20), Duration.ofSeconds(1)));
        when(preparer.prepare("kb", request, List.of())).thenReturn(List.of(success, timeout, overloaded));
        when(analyzer.analyze(any(), eq(request), any())).thenAnswer(invocation -> {
            PreparedDiscoverySource source = invocation.getArgument(0);
            if (source.sourceId().equals("source-timeout")) {
                Thread.sleep(200);
            }
            if (source.sourceId().equals("source-overloaded")) {
                throw new RejectedExecutionException("provider overloaded");
            }
            return new DiscoverySourceAnalyzer.SourceAnalysis(source, List.of(), List.of());
        });
        when(aggregator.aggregate(any(), eq(request))).thenReturn(new DiscoveryAggregator.AggregateResult(
            List.of(), List.of(), List.of(), objectMapper.createObjectNode()));

        SchemaDiscoveryResponse response = service.discover("kb", request, List.of());

        assertThat(response.status()).isEqualTo(ResponseStatus.PARTIAL);
        assertThat(response.sourceOutcomes()).extracting(outcome -> outcome.failureCategory())
            .containsExactly(null, io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.FailureCategory.TIMEOUT,
                io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.FailureCategory.OVERLOADED);
    }

    @Test
    void neverExceedsConfiguredSourceConcurrency() {
        List<PreparedDiscoverySource> sources = List.of(source("source-1"), source("source-2"), source("source-3"), source("source-4"));
        SchemaDiscoveryRequest request = request();
        when(preparer.prepare("kb", request, List.of())).thenReturn(sources);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        when(analyzer.analyze(any(), eq(request), any())).thenAnswer(invocation -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(30);
                PreparedDiscoverySource source = invocation.getArgument(0);
                return new DiscoverySourceAnalyzer.SourceAnalysis(source, List.of(), List.of());
            } finally {
                active.decrementAndGet();
            }
        });
        when(aggregator.aggregate(any(), eq(request))).thenReturn(new DiscoveryAggregator.AggregateResult(
            List.of(), List.of(), List.of(), objectMapper.createObjectNode()));

        SchemaDiscoveryResponse response = service.discover("kb", request, List.of());

        assertThat(response.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(maximum.get()).isLessThanOrEqualTo(2);
    }

    @Test
    void normalLogsExcludeAllDiscoveryContent(CapturedOutput output) {
        String sourceText = "private-source-text-9bd132";
        String instructions = "private-guidance-72aa31";
        String candidateContent = "PrivateCandidate8cc910";
        String schemaContent = "private-schema-content-3fb921";
        SchemaDiscoveryRequest request = new SchemaDiscoveryRequest(List.of(),
            List.of(new SchemaDiscoveryRequest.TextSource("sample", sourceText)), instructions,
            SchemaDiscoveryRequest.DiscoveryGuidance.empty());
        PreparedDiscoverySource source = source("source-private");
        when(preparer.prepare("kb", request, List.of())).thenReturn(List.of(source));
        when(analyzer.analyze(eq(source), eq(request), any()))
            .thenReturn(new DiscoverySourceAnalyzer.SourceAnalysis(source, List.of(), List.of()));
        com.fasterxml.jackson.databind.node.ObjectNode schema = objectMapper.createObjectNode();
        schema.put("description", schemaContent);
        when(aggregator.aggregate(any(), eq(request))).thenReturn(new DiscoveryAggregator.AggregateResult(
            List.of(), List.of(), List.of(new io.github.vfedoriv.graphrag.dto.SchemaDiscoveryResponse.Warning(
                "TEST", candidateContent, candidateContent, 1)), schema));

        service.discover("kb", request, List.of());

        assertThat(output.getAll()).doesNotContain(sourceText, instructions, candidateContent, schemaContent);
    }

    private PreparedDiscoverySource source(String id) {
        return new PreparedDiscoverySource(id, SourceType.TEXT, id, null, id + "-fingerprint",
            List.of(new PreparedDiscoverySource.AnalysisChunk(id + "-chunk-001", 1, "text", "chunk-fingerprint")));
    }

    private SchemaDiscoveryRequest request() {
        return new SchemaDiscoveryRequest(List.of(), List.of(new SchemaDiscoveryRequest.TextSource("sample", "text")),
            null, SchemaDiscoveryRequest.DiscoveryGuidance.empty());
    }
}
