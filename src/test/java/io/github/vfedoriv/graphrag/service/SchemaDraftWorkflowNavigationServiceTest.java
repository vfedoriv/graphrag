package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.schemas.evaluation.application.SchemaDraftEvaluationEligibilityService;

import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftAnalysisRetryEligibilityService;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftJsonSupport;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftWorkflowNavigationService;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftKnowledgeBases;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftSchemaLookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.AnalysisRunPageResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationEligibleDocumentPageResponse;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftSourceResultRepository;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.SchemaReprocessingPlanRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftEvaluationSummaries;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftReprocessingSummaries;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class SchemaDraftWorkflowNavigationServiceTest {
    private final SchemaDraftAnalysisRunRepository analysisRepository = mock(SchemaDraftAnalysisRunRepository.class);
    private final DraftEvaluationSummaries evaluationSummaries = mock(DraftEvaluationSummaries.class);
    private final DraftReprocessingSummaries reprocessingSummaries = mock(DraftReprocessingSummaries.class);
    private final SchemaDraftSourceRepository sourceRepository = mock(SchemaDraftSourceRepository.class);
    private final DraftKnowledgeBases knowledgeBaseRepository = mock(DraftKnowledgeBases.class);
    private final DraftSchemaLookup schemaRepository = mock(DraftSchemaLookup.class);
    private final SchemaDraftJsonSupport jsonSupport = new SchemaDraftJsonSupport(new ObjectMapper());
    private final SchemaDraftAnalysisRetryEligibilityService analysisRetryEligibilityService =
        new SchemaDraftAnalysisRetryEligibilityService(analysisRepository, sourceRepository);
    private final SchemaDraftWorkflowNavigationService service = new SchemaDraftWorkflowNavigationService(
        analysisRepository, evaluationSummaries, reprocessingSummaries, sourceRepository,
        jsonSupport, analysisRetryEligibilityService);

    @Test
    void derivesRunningAggregateAndStaleCurrentnessFromAuthoritativeDraftState() {
        SchemaDraftNode draft = draft();
        SchemaDraftSourceNode source = source();
        when(sourceRepository.findByDraftIdAndStatusOrderByCreatedAtAsc("draft", SchemaDraftSourceStatus.ACTIVE))
            .thenReturn(List.of(source));

        SchemaDraftAnalysisRunNode running = analysis("running", SchemaDraftAnalysisStatus.RUNNING);
        running.setDraftRevision(2);
        running.setGuidanceRevision(1);
        running.setGuidanceFingerprint("guidance");
        running.setSourceMembershipFingerprint(jsonSupport.fingerprint("source:1:sha"));
        assertThat(service.isAnalysisCurrent(draft, running)).isTrue();

        running.setDraftRevision(1);
        assertThat(service.isAnalysisCurrent(draft, running)).isFalse();

        SchemaDraftAnalysisRunNode terminal = analysis("terminal", SchemaDraftAnalysisStatus.COMPLETED);
        terminal.setAggregateRevisionId("aggregate");
        terminal.setDraftRevision(1);
        assertThat(service.isAnalysisCurrent(draft, terminal)).isTrue();

    }

    @Test
    void mapsPersistedRetryabilityAndCurrentRetryEligibilityWithSharedPageInputs() {
        SchemaDraftNode draft = draft();
        SchemaDraftAnalysisRunNode run = analysis("retry", SchemaDraftAnalysisStatus.COMPLETED);
        run.setAggregateRevisionId("aggregate");
        run.setRetryOfRunId("root");
        run.setTotalSources(2);
        run.setSucceededSources(1);
        run.setFailedSources(1);
        run.setRetryable(false);
        when(analysisRepository.findPageByDraftId("draft", PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(run), PageRequest.of(0, 20), 1));
        when(sourceRepository.existsByDraftIdAndStatus("draft", SchemaDraftSourceStatus.ACTIVE)).thenReturn(true);
        when(analysisRepository.findFirstByDraftIdAndStatusOrderByCreatedAtDesc(
            "draft", SchemaDraftAnalysisStatus.RUNNING)).thenReturn(Optional.empty());

        AnalysisRunPageResponse page = service.analysisPage(draft, 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().retryOfRunId()).isEqualTo("root");
        assertThat(page.getContent().getFirst().current()).isTrue();
        assertThat(page.getContent().getFirst().retryable()).isFalse();
        assertThat(page.getContent().getFirst().canRetry()).isTrue();
        assertThat(page.getContent().getFirst().statusLocation()).endsWith("/analysis-runs/retry");
        verify(sourceRepository).existsByDraftIdAndStatus("draft", SchemaDraftSourceStatus.ACTIVE);
        verify(analysisRepository).findFirstByDraftIdAndStatusOrderByCreatedAtDesc(
            "draft", SchemaDraftAnalysisStatus.RUNNING);
    }

    @Test
    void resolvesDraftListReferencesWithOneBoundedQueryPerWorkflowType() {
        SchemaDraftNode first = draft();
        SchemaDraftNode second = draft();
        second.setId("draft-2");
        List<String> draftIds = List.of("draft", "draft-2");
        when(sourceRepository.findActiveForDraftIds(draftIds)).thenReturn(List.of());
        when(analysisRepository.findCurrentForDraftIds(draftIds)).thenReturn(List.of());
        when(evaluationSummaries.latest(List.of(new DraftEvaluationSummaries.Context("draft", 2, "aggregate"),
            new DraftEvaluationSummaries.Context("draft-2", 2, "aggregate")))).thenReturn(java.util.Map.of());
        when(reprocessingSummaries.latest(draftIds)).thenReturn(java.util.Map.of());

        assertThat(service.references(List.of(first, second))).containsOnlyKeys("draft", "draft-2");

        verify(sourceRepository).findActiveForDraftIds(draftIds);
        verify(analysisRepository).findCurrentForDraftIds(draftIds);
        verify(evaluationSummaries).latest(List.of(new DraftEvaluationSummaries.Context("draft", 2, "aggregate"),
            new DraftEvaluationSummaries.Context("draft-2", 2, "aggregate")));
        verify(reprocessingSummaries).latest(draftIds);
    }

    private SchemaDraftNode draft() {
        SchemaDraftNode draft = new SchemaDraftNode();
        draft.setId("draft");
        draft.setKnowledgeBaseId("kb");
        draft.setRevision(2);
        draft.setGuidanceRevision(1);
        draft.setGuidanceFingerprint("guidance");
        draft.setCurrentAggregateId("aggregate");
        draft.setStatus(SchemaDraftStatus.OPEN);
        return draft;
    }

    private SchemaDraftSourceNode source() {
        SchemaDraftSourceNode source = new SchemaDraftSourceNode();
        source.setId("source");
        source.setDraftId("draft");
        source.setRevision(1);
        source.setSha256("sha");
        source.setStatus(SchemaDraftSourceStatus.ACTIVE);
        return source;
    }

    private SchemaDraftAnalysisRunNode analysis(String id, SchemaDraftAnalysisStatus status) {
        SchemaDraftAnalysisRunNode run = new SchemaDraftAnalysisRunNode();
        run.setId(id);
        run.setDraftId("draft");
        run.setKnowledgeBaseId("kb");
        run.setStatus(status);
        run.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return run;
    }

}
