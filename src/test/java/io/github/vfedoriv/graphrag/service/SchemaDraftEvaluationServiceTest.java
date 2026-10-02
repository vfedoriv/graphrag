package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import io.github.vfedoriv.graphrag.schemas.evaluation.application.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.*;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.*;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaRegistryCapabilities;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.*;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(MockitoExtension.class)
class SchemaDraftEvaluationServiceTest {
    @Mock private DraftAdmissions admissions;
    @Mock private DraftReviewInputs reviewInputs;
    @Mock private EvaluationDocuments documents;
    @Mock private EvaluationDryExtraction extraction;
    @Mock private SchemaDraftEvaluationRunRepository runRepository;
    @Mock private SchemaDraftEvaluationOutcomeRepository outcomeRepository;
    @Mock private EvaluationProfiles profiles;
    @Mock private SchemaDraftEvaluationMetricsCalculator metricsCalculator;
    @Mock private SchemaRegistryCapabilities registry;
    @Mock private EvaluationJsonSupport jsonSupport;
    @Mock private SchemaDraftEvaluationContractMapper contractMapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private AiObservationService observationService;
    @Mock private SchemaDraftEvaluationEligibilityService eligibilityService;
    @Mock private EvaluationHistoryService historyService;
    @Mock private EvaluationCheckpointService checkpointService;
    @Mock private TaskExecutor executor;
    @InjectMocks private SchemaDraftEvaluationService service;

    @Test
    void rejectsAggregateChangeAfterEligibilityResolutionBeforeCreatingRun() {
        DraftAdmissions.Draft captured = draft("aggregate-1");
        DraftAdmissions.Draft changed = draft("aggregate-2");
        EvaluationDocuments.Metadata document = document();
        SchemaDraftEvaluationEligibilityService.EligibilitySnapshot eligibility = ready();
        when(admissions.requireMutable("kb", "draft", 2)).thenReturn(captured, changed);
        when(eligibilityService.resolve(captured)).thenReturn(eligibility);
        when(documents.inspectOwned("kb", "document")).thenReturn(Optional.of(document));
        when(eligibilityService.isEligible(document, eligibility)).thenReturn(true);

        assertThatThrownBy(() -> service.start("kb", "draft", request()))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("aggregate changed");

        verifyNoInteractions(runRepository, executor, extraction);
    }

    @Test
    void rejectsRevisionChangeAfterEligibilityResolutionBeforeCreatingRun() {
        DraftAdmissions.Draft captured = draft("aggregate-1");
        EvaluationDocuments.Metadata document = document();
        SchemaDraftEvaluationEligibilityService.EligibilitySnapshot eligibility = ready();
        when(admissions.requireMutable("kb", "draft", 2))
            .thenReturn(captured)
            .thenThrow(new ConflictException("Stale schema draft revision; current revision is 3"));
        when(eligibilityService.resolve(captured)).thenReturn(eligibility);
        when(documents.inspectOwned("kb", "document")).thenReturn(Optional.of(document));
        when(eligibilityService.isEligible(document, eligibility)).thenReturn(true);

        assertThatThrownBy(() -> service.start("kb", "draft", request()))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Stale schema draft revision");

        verifyNoInteractions(runRepository, executor, extraction);
    }

    private DraftAdmissions.Draft draft(String aggregateId) {
        return new DraftAdmissions.Draft("draft", "kb", 2, null, "target", 1, aggregateId, "{}", true, null, null);
    }

    private EvaluationDocuments.Metadata document() {
        return new EvaluationDocuments.Metadata("document", "held-out.txt", "text/plain", 1, "held-out-sha", null);
    }

    private StartEvaluationRequest request() {
        return new StartEvaluationRequest(2, List.of("document"), false);
    }

    private SchemaDraftEvaluationEligibilityService.EligibilitySnapshot ready() {
        return new SchemaDraftEvaluationEligibilityService.EligibilitySnapshot(
            EvaluationReadiness.READY, null, Set.of(), Set.of());
    }
}
