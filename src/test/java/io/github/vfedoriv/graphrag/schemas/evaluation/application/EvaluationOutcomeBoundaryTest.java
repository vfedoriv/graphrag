package io.github.vfedoriv.graphrag.schemas.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.config.LegacyJacksonConfiguration;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.schemas.contracts.*;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.EvaluationObservations.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.mockito.InOrder;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(OutputCaptureExtension.class)
class EvaluationOutcomeBoundaryTest {
    private final DraftAdmissions admissions = mock(DraftAdmissions.class);
    private final DraftReviewInputs review = mock(DraftReviewInputs.class);
    private final EvaluationDocuments documents = mock(EvaluationDocuments.class);
    private final EvaluationDryExtraction extraction = mock(EvaluationDryExtraction.class);
    private final SchemaDraftEvaluationRunRepository runs = mock(SchemaDraftEvaluationRunRepository.class);
    private final SchemaDraftEvaluationOutcomeRepository outcomes = mock(SchemaDraftEvaluationOutcomeRepository.class);
    private final EvaluationProfiles profiles = mock(EvaluationProfiles.class);
    private final SchemaRegistryCapabilities registry = mock(SchemaRegistryCapabilities.class);
    private final SchemaDraftEvaluationEligibilityService eligibility = mock(SchemaDraftEvaluationEligibilityService.class);
    private final EvaluationCheckpointService checkpoint = mock(EvaluationCheckpointService.class);
    private final TaskExecutor executor = mock(TaskExecutor.class);
    private final ObjectMapper mapper = new LegacyJacksonConfiguration().legacyObjectMapper();
    private final EvaluationJsonSupport json = new EvaluationJsonSupport(mapper);
    private final SchemaDraftEvaluationService service = service();
    private final SchemaDocument schema = new SchemaDocument("people", 1, null,
        List.of(new SchemaDocument.NodeDefinition("Person", null, List.of("id"),
            List.of(new SchemaDocument.PropertyDefinition("id", "STRING", true)))), List.of(), List.of(), List.of());

    @Test
    void sourceChecksPrecedeReuseAndAvailabilityAndReuseAvoidsPreparation() throws Exception {
        SchemaDraftEvaluationOutcomeNode missing = outcome("missing");
        SchemaDraftEvaluationOutcomeNode changed = outcome("changed");
        SchemaDraftEvaluationOutcomeNode reused = outcome("reused");
        SchemaDraftEvaluationOutcomeNode unavailable = outcome("unavailable");
        SchemaDraftEvaluationRunNode run = execution(List.of(missing, changed, reused, unavailable), false, false);
        when(documents.inspectOwned("kb", "missing")).thenReturn(Optional.empty());
        when(documents.inspectOwned("kb", "changed")).thenReturn(Optional.of(metadata("changed", "new-sha")));
        when(documents.inspectOwned("kb", "reused")).thenReturn(Optional.of(metadata("reused", "sha")));
        when(documents.inspectOwned("kb", "unavailable")).thenReturn(Optional.of(metadata("unavailable", "sha")));
        SchemaDraftEvaluationOutcomeNode prior = outcome("prior");
        prior.setMetricsJson(json.canonical(new SchemaDraftEvaluationMetricsCalculator().combine(List.of())));
        prior.setChunkCount(7);
        when(outcomes.findFirstByDraftIdAndReuseKeyAndStatusInOrderByCompletedAtDesc(eq("draft"), eq("reused"), anyList()))
            .thenReturn(Optional.of(prior));

        service.execute("run");

        assertThat(missing.getStatus()).isEqualTo(SchemaDraftEvaluationOutcomeStatus.STALE_SOURCE);
        assertThat(changed.getStatus()).isEqualTo(SchemaDraftEvaluationOutcomeStatus.STALE_SOURCE);
        assertThat(reused.getStatus()).isEqualTo(SchemaDraftEvaluationOutcomeStatus.REUSED);
        assertThat(reused.getChunkCount()).isEqualTo(7);
        assertThat(reused.getReusedFromOutcomeId()).isEqualTo("prior");
        assertThat(unavailable.getFailureCategory()).isEqualTo("AI_CLIENT_UNAVAILABLE");
        assertThat(run.getStatus()).isEqualTo(SchemaDraftEvaluationStatus.PARTIAL);
        assertThat(run.getSucceededDocuments()).isEqualTo(1);
        assertThat(run.getStaleDocuments()).isEqualTo(2);
        verify(documents, never()).prepareOwned(anyString(), anyString());
        verify(extraction, never()).extract(any(), anyString(), anyString());
        verify(outcomes, never()).findFirstByDraftIdAndReuseKeyAndStatusInOrderByCompletedAtDesc(eq("draft"), eq("missing"), anyList());
        verify(outcomes, never()).findFirstByDraftIdAndReuseKeyAndStatusInOrderByCompletedAtDesc(eq("draft"), eq("changed"), anyList());
    }

    @Test
    void preparationAndExtractionFailuresRemainRetryableAndOtherDocumentsFinishWithEvidence(CapturedOutput output) throws Exception {
        SchemaDraftEvaluationOutcomeNode preparationFailure = outcome("parse-failure");
        SchemaDraftEvaluationOutcomeNode extractionFailure = outcome("model-failure");
        SchemaDraftEvaluationOutcomeNode success = outcome("success");
        SchemaDraftEvaluationRunNode run = execution(List.of(preparationFailure, extractionFailure, success), true, true);
        for (String id : List.of("parse-failure", "model-failure", "success"))
            when(documents.inspectOwned("kb", id)).thenReturn(Optional.of(metadata(id, "sha")));
        when(documents.prepareOwned("kb", "parse-failure")).thenThrow(new IllegalArgumentException("private document content"));
        when(documents.prepareOwned("kb", "model-failure")).thenReturn(new EvaluationDocuments.Prepared("model-failure", List.of("bad")));
        when(documents.prepareOwned("kb", "success")).thenReturn(new EvaluationDocuments.Prepared("success", List.of("first", "second")));
        when(review.aggregate("kb", "draft", "aggregate")).thenReturn(new DraftReviewInputs.Aggregate(
            "[{\"identity\":\"node:Person\",\"supportCount\":1,\"origins\":[\"GUIDED\"]}]", Set.of(), List.of()));
        when(review.intendedQuestions("{}")).thenReturn(List.of("Who is present?"));
        when(extraction.extract(schema, "bad", "profile")).thenThrow(new IllegalStateException("private model response"));
        Result raw = new Result(List.of(new Node("Person", Map.of("id", "p"), 0.9), new Node("Unknown", Map.of(), 0.4)),
            List.of(new Relationship("UNKNOWN", "Person", Map.of("id", "p"), "Unknown", Map.of(), Map.of(), 0.1)));
        Observation observation = new Observation(raw, new Result(List.of(raw.nodes().getFirst()), List.of()));
        when(extraction.extract(schema, "first", "profile")).thenReturn(observation);
        when(extraction.extract(schema, "second", "profile")).thenReturn(observation);

        service.execute("run");

        assertThat(preparationFailure.getFailureCategory()).isEqualTo("ILLEGALARGUMENTEXCEPTION");
        assertThat(extractionFailure.getFailureCategory()).isEqualTo("ILLEGALSTATEEXCEPTION");
        assertThat(preparationFailure.isRetryable()).isTrue();
        assertThat(extractionFailure.isRetryable()).isTrue();
        assertThat(success.getChunkCount()).isEqualTo(2);
        assertThat(success.getEvidenceCoordinatesJson()).isEqualTo("[\"node:Person\",\"node:Unknown\",\"relationship:UNKNOWN:Person:Unknown\"]");
        assertThat(new SchemaDraftEvaluationContractMapper(json, mapper).domainMetrics(success.getMetricsJson()).recognizedEntities()).isEqualTo(2);
        assertThat(run.getStatus()).isEqualTo(SchemaDraftEvaluationStatus.PARTIAL);
        assertThat(run.getAdvisoryJson()).contains("COMPLETED_WITHOUT_MODEL_JUDGMENT", "POSSIBLE_NOISE", "UNASSESSED");
        assertThat(output.getAll()).doesNotContain("private document content", "private model response", "Who is present?");
        InOrder ordered = inOrder(documents, extraction);
        ordered.verify(documents).prepareOwned("kb", "success");
        ordered.verify(extraction).extract(schema, "first", "profile");
        ordered.verify(extraction).extract(schema, "second", "profile");
    }

    @Test
    void creationPreservesCanonicalSnapshotsFingerprintsAndCheckpointsBeforeScheduling() {
        DraftAdmissions.Draft draft = new DraftAdmissions.Draft("draft", "kb", 2, null, "people", 1, "aggregate", "{}", true, null, null);
        when(admissions.requireMutable("kb", "draft", 2)).thenReturn(draft);
        SchemaDraftEvaluationEligibilityService.EligibilitySnapshot ready = new SchemaDraftEvaluationEligibilityService.EligibilitySnapshot(
            io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationReadiness.READY, null, Set.of(), Set.of());
        when(eligibility.resolve(draft)).thenReturn(ready);
        EvaluationDocuments.Metadata document = metadata("document", "sha");
        when(documents.inspectOwned("kb", "document")).thenReturn(Optional.of(document));
        when(eligibility.isEligible(document, ready)).thenReturn(true);
        String projection = json.canonical(schema);
        String decisions = "[{\"createdAt\":\"2026-07-21T08:15:30Z\",\"decision\":\"ACCEPT\"}]";
        when(review.projection("kb", "draft")).thenReturn(new DraftReviewInputs.Projection("aggregate", 2, projection));
        when(review.canonicalDecisions("kb", "draft")).thenReturn(decisions);
        when(profiles.activeProfile("kb")).thenReturn(new EvaluationProfiles.Profile("profile", 3));
        when(checkpoint.create(any(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.ArgumentCaptor<SchemaDraftEvaluationRunNode> captured = org.mockito.ArgumentCaptor.forClass(SchemaDraftEvaluationRunNode.class);
        org.mockito.ArgumentCaptor<List<SchemaDraftEvaluationOutcomeNode>> capturedOutcomes = org.mockito.ArgumentCaptor.forClass(List.class);

        service.start("kb", "draft", new io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.StartEvaluationRequest(
            2, List.of("document", "document"), false));

        InOrder ordered = inOrder(checkpoint, executor);
        ordered.verify(checkpoint).create(captured.capture(), capturedOutcomes.capture());
        ordered.verify(executor).execute(any(Runnable.class));
        SchemaDraftEvaluationRunNode run = captured.getValue();
        String snapshot = "[{\"documentId\":\"document\",\"sha256\":\"sha\"}]";
        assertThat(run.getProjectionJson()).isEqualTo(projection);
        assertThat(run.getDocumentSnapshotJson()).isEqualTo(snapshot);
        assertThat(run.getDecisionsJson()).isEqualTo(decisions);
        assertThat(run.getDecisionsFingerprint()).isEqualTo(json.fingerprint(decisions));
        assertThat(run.getSnapshotFingerprint()).isEqualTo(json.fingerprint(projection + snapshot + "profile3"
            + SchemaDraftEvaluationContracts.CONTRACT_REVISION + "{\"advisoryEnabled\":false}"));
        assertThat(capturedOutcomes.getValue()).hasSize(1);
        assertThat(capturedOutcomes.getValue().getFirst().getReuseKey()).isEqualTo(json.fingerprint("documentsha"
            + json.fingerprint(projection) + "profile3" + SchemaDraftEvaluationContracts.PROMPT_REVISION
            + SchemaDraftEvaluationContracts.CONTRACT_REVISION + "{\"advisoryEnabled\":false}"));
        verifyNoInteractions(extraction);
    }

    private SchemaDraftEvaluationRunNode execution(List<SchemaDraftEvaluationOutcomeNode> values, boolean available, boolean advisory) {
        SchemaDraftEvaluationRunNode run = new SchemaDraftEvaluationRunNode();
        run.setId("run"); run.setDraftId("draft"); run.setKnowledgeBaseId("kb"); run.setAggregateRevisionId("aggregate");
        run.setProjectionJson("projection"); run.setProjectionContentHash("hash"); run.setAiProfileId("profile");
        run.setAiProfileRevision(3); run.setPromptRevision(SchemaDraftEvaluationContracts.PROMPT_REVISION);
        run.setContractRevision(SchemaDraftEvaluationContracts.CONTRACT_REVISION); run.setTotalDocuments(values.size());
        run.setGuidanceJson("{}"); run.setSettingsJson("{\"advisoryEnabled\":" + advisory + "}");
        when(runs.claim(eq("run"), anyString(), any(), any())).thenReturn(1L);
        when(runs.findById("run")).thenReturn(Optional.of(run));
        when(registry.parseAndValidate("projection")).thenReturn(new SchemaValidationResult(schema, List.of()));
        when(extraction.available()).thenReturn(available);
        when(outcomes.findByRunIdOrderByDocumentIdAsc("run")).thenReturn(values);
        when(outcomes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documents.captureOwned(eq("kb"), anyString())).thenAnswer(invocation -> {
            String documentId = invocation.getArgument(1);
            return documents.inspectOwned("kb", documentId).map(metadata -> new EvaluationDocuments.Source() {
                @Override public EvaluationDocuments.Metadata metadata() { return metadata; }
                @Override public EvaluationDocuments.Prepared prepare() throws java.io.IOException { return documents.prepareOwned("kb", documentId); }
            });
        });
        return run;
    }
    private SchemaDraftEvaluationOutcomeNode outcome(String id) {
        SchemaDraftEvaluationOutcomeNode outcome = new SchemaDraftEvaluationOutcomeNode();
        outcome.setId(id); outcome.setRunId("run"); outcome.setDraftId("draft"); outcome.setDocumentId(id);
        outcome.setDocumentSha256("sha"); outcome.setReuseKey(id); outcome.setStatus(SchemaDraftEvaluationOutcomeStatus.QUEUED);
        return outcome;
    }
    private EvaluationDocuments.Metadata metadata(String id, String hash) { return new EvaluationDocuments.Metadata(id, id + ".txt", "text/plain", 0, hash, null); }
    private SchemaDraftEvaluationService service() {
        AiObservationService observations = mock(AiObservationService.class);
        when(observations.startWorkflow(any())).thenReturn(mock(AiObservationScope.class));
        return new SchemaDraftEvaluationService(admissions, review, documents, extraction, runs, outcomes, profiles,
            new SchemaDraftEvaluationMetricsCalculator(), registry, json, new SchemaDraftEvaluationContractMapper(json, mapper),
            mapper, observations, eligibility, mock(EvaluationHistoryService.class), checkpoint, executor);
    }
}
