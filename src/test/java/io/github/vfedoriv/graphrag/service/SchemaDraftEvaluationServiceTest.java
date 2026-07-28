package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.config.SchemaDraftEvaluationProperties;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationReadiness;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.StartEvaluationRequest;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionValidationService;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationOutcomeRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(MockitoExtension.class)
class SchemaDraftEvaluationServiceTest {
    @Mock private SchemaDraftLifecycleService lifecycleService;
    @Mock private SchemaDraftReviewService reviewService;
    @Mock private DocumentUploadRepository documentRepository;
    @Mock private SchemaDraftEvaluationRunRepository runRepository;
    @Mock private SchemaDraftEvaluationOutcomeRepository outcomeRepository;
    @Mock private SchemaDraftAggregateRevisionRepository aggregateRepository;
    @Mock private KnowledgeBaseService knowledgeBaseService;
    @Mock private DocumentUploadService uploadService;
    @Mock private DocumentParsingService parsingService;
    @Mock private ChunkingService chunkingService;
    @Mock private ObjectProvider<GraphExtractionClient> extractionClients;
    @Mock private GraphExtractionValidationService validationService;
    @Mock private SchemaDraftEvaluationMetricsCalculator metricsCalculator;
    @Mock private io.github.vfedoriv.graphrag.schema.SchemaParser schemaParser;
    @Mock private SchemaDraftJsonSupport jsonSupport;
    @Mock private SchemaDraftGuidanceMapper guidanceMapper;
    @Mock private SchemaDraftEvaluationContractMapper contractMapper;
    @Mock private ObjectMapper objectMapper;
    private final SchemaDraftEvaluationProperties properties =
        new SchemaDraftEvaluationProperties(1, 1, 1, false, false);
    @Mock private AiObservationService observationService;
    @Mock private SchemaDraftEvaluationEligibilityService eligibilityService;
    @Mock private SchemaDraftWorkflowNavigationService workflowNavigationService;
    @Mock private TaskExecutor executor;
    @InjectMocks private SchemaDraftEvaluationService service;

    @Test
    void rejectsAggregateChangeAfterEligibilityResolutionBeforeCreatingRun() {
        SchemaDraftNode captured = draft("aggregate-1");
        SchemaDraftNode changed = draft("aggregate-2");
        DocumentUploadNode document = document();
        SchemaDraftEvaluationEligibilityService.EligibilitySnapshot eligibility = ready();
        when(lifecycleService.requireMutable("kb", "draft", 2)).thenReturn(captured, changed);
        when(eligibilityService.resolve(captured)).thenReturn(eligibility);
        when(documentRepository.findByIdAndKnowledgeBaseId("document", "kb")).thenReturn(Optional.of(document));
        when(eligibilityService.isEligible(document, eligibility)).thenReturn(true);

        assertThatThrownBy(() -> service.start("kb", "draft", request()))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("aggregate changed");

        verifyNoInteractions(runRepository, executor, extractionClients);
    }

    @Test
    void rejectsRevisionChangeAfterEligibilityResolutionBeforeCreatingRun() {
        SchemaDraftNode captured = draft("aggregate-1");
        DocumentUploadNode document = document();
        SchemaDraftEvaluationEligibilityService.EligibilitySnapshot eligibility = ready();
        when(lifecycleService.requireMutable("kb", "draft", 2))
            .thenReturn(captured)
            .thenThrow(new ConflictException("Stale schema draft revision; current revision is 3"));
        when(eligibilityService.resolve(captured)).thenReturn(eligibility);
        when(documentRepository.findByIdAndKnowledgeBaseId("document", "kb")).thenReturn(Optional.of(document));
        when(eligibilityService.isEligible(document, eligibility)).thenReturn(true);

        assertThatThrownBy(() -> service.start("kb", "draft", request()))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Stale schema draft revision");

        verifyNoInteractions(runRepository, executor, extractionClients);
    }

    private SchemaDraftNode draft(String aggregateId) {
        SchemaDraftNode draft = new SchemaDraftNode();
        draft.setId("draft");
        draft.setKnowledgeBaseId("kb");
        draft.setRevision(2);
        draft.setCurrentAggregateId(aggregateId);
        return draft;
    }

    private DocumentUploadNode document() {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("document");
        document.setKnowledgeBaseId("kb");
        document.setSha256("held-out-sha");
        return document;
    }

    private StartEvaluationRequest request() {
        return new StartEvaluationRequest(2, List.of("document"), false);
    }

    private SchemaDraftEvaluationEligibilityService.EligibilitySnapshot ready() {
        return new SchemaDraftEvaluationEligibilityService.EligibilitySnapshot(
            EvaluationReadiness.READY, null, Set.of(), Set.of());
    }
}
