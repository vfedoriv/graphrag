package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.AnalysisRunPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationEligibleDocumentPageResponse;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceResultRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class SchemaDraftWorkflowNavigationServiceTest {
    private final SchemaDraftAnalysisRunRepository analysisRepository = mock(SchemaDraftAnalysisRunRepository.class);
    private final SchemaDraftEvaluationRunRepository evaluationRepository = mock(SchemaDraftEvaluationRunRepository.class);
    private final SchemaReprocessingPlanRepository reprocessingRepository = mock(SchemaReprocessingPlanRepository.class);
    private final SchemaDraftSourceRepository sourceRepository = mock(SchemaDraftSourceRepository.class);
    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final SchemaDefinitionRepository schemaRepository = mock(SchemaDefinitionRepository.class);
    private final SchemaDraftJsonSupport jsonSupport = new SchemaDraftJsonSupport(new ObjectMapper());
    private final SchemaDraftAnalysisRetryEligibilityService analysisRetryEligibilityService =
        new SchemaDraftAnalysisRetryEligibilityService(analysisRepository, sourceRepository);
    private final SchemaDraftWorkflowNavigationService service = new SchemaDraftWorkflowNavigationService(
        analysisRepository, evaluationRepository, reprocessingRepository, sourceRepository,
        knowledgeBaseRepository, schemaRepository, jsonSupport, analysisRetryEligibilityService);

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

        SchemaDraftEvaluationRunNode evaluation = new SchemaDraftEvaluationRunNode();
        evaluation.setDraftRevision(2);
        evaluation.setAggregateRevisionId("aggregate");
        evaluation.setProjectionContentHash("projection");
        assertThat(service.evaluationCurrent(draft, evaluation)).isTrue();
        evaluation.setDraftRevision(1);
        assertThat(service.evaluationCurrent(draft, evaluation)).isFalse();
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
    void eligibilityUsesOnlySuccessfulContributorsFromTheCurrentAggregate() {
        SchemaDraftLifecycleService lifecycleService = mock(SchemaDraftLifecycleService.class);
        DocumentUploadRepository documentRepository = mock(DocumentUploadRepository.class);
        SchemaDraftSourceResultRepository resultRepository = mock(SchemaDraftSourceResultRepository.class);
        SchemaDraftEvaluationEligibilityService eligibilityService = new SchemaDraftEvaluationEligibilityService(
            lifecycleService, documentRepository, resultRepository);
        SchemaDraftNode draft = draft();
        DocumentUploadNode evidence = document("evidence");
        DocumentUploadNode heldOut = document("held-out");
        when(lifecycleService.requireOwned("kb", "draft")).thenReturn(draft);
        when(documentRepository.findPageByKnowledgeBaseId("kb", PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(evidence, heldOut), PageRequest.of(0, 20), 2));
        when(resultRepository.findContributingSourceSha256s("draft")).thenReturn(List.of("evidence-sha"));
        when(resultRepository.findHistoricalContributingDocumentIds("draft")).thenReturn(List.of());

        EvaluationEligibleDocumentPageResponse page = eligibilityService.list("kb", "draft", 0, 20);

        assertThat(page.getDraftRevision()).isEqualTo(2);
        assertThat(page.getCurrentAggregateId()).isEqualTo("aggregate");
        assertThat(page.getReadiness()).hasToString("READY");
        assertThat(page.getBlockingReason()).isNull();
        assertThat(page.getContent()).extracting(value -> value.documentId() + ":" + value.eligible())
            .containsExactly("evidence:false", "held-out:true");
        assertThat(page.getContent().getFirst().ineligibilityReason()).hasToString("ACTIVE_DISCOVERY_EVIDENCE");
        assertThat(page.getContent().get(1).ineligibilityReason()).isNull();
    }

    @Test
    void eligibilityRequiresCurrentAnalysisAndPreservesDocumentPageMetadata() {
        SchemaDraftLifecycleService lifecycleService = mock(SchemaDraftLifecycleService.class);
        DocumentUploadRepository documentRepository = mock(DocumentUploadRepository.class);
        SchemaDraftSourceResultRepository resultRepository = mock(SchemaDraftSourceResultRepository.class);
        SchemaDraftEvaluationEligibilityService eligibilityService = new SchemaDraftEvaluationEligibilityService(
            lifecycleService, documentRepository, resultRepository);
        SchemaDraftNode draft = draft();
        draft.setCurrentAggregateId(null);
        when(lifecycleService.requireOwned("kb", "draft")).thenReturn(draft);
        when(documentRepository.findPageByKnowledgeBaseId("kb", PageRequest.of(1, 1)))
            .thenReturn(new PageImpl<>(List.of(document("candidate")), PageRequest.of(1, 1), 3));

        EvaluationEligibleDocumentPageResponse page = eligibilityService.list("kb", "draft", 1, 1);

        assertThat(page.getReadiness()).hasToString("NOT_READY");
        assertThat(page.getBlockingReason()).hasToString("DRAFT_ANALYSIS_REQUIRED");
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent().getFirst().eligible()).isFalse();
        assertThat(page.getContent().getFirst().ineligibilityReason()).hasToString("DRAFT_ANALYSIS_REQUIRED");
        verifyNoInteractions(resultRepository);
    }

    @Test
    void eligibilityClassifiesDuplicateFingerprintsAndHistoricalDocumentFallback() {
        SchemaDraftLifecycleService lifecycleService = mock(SchemaDraftLifecycleService.class);
        DocumentUploadRepository documentRepository = mock(DocumentUploadRepository.class);
        SchemaDraftSourceResultRepository resultRepository = mock(SchemaDraftSourceResultRepository.class);
        SchemaDraftEvaluationEligibilityService eligibilityService = new SchemaDraftEvaluationEligibilityService(
            lifecycleService, documentRepository, resultRepository);
        SchemaDraftNode draft = draft();
        DocumentUploadNode firstDuplicate = document("first");
        DocumentUploadNode secondDuplicate = document("second");
        secondDuplicate.setSha256(firstDuplicate.getSha256());
        DocumentUploadNode historical = document("historical");
        when(lifecycleService.requireOwned("kb", "draft")).thenReturn(draft);
        when(documentRepository.findPageByKnowledgeBaseId("kb", PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(firstDuplicate, secondDuplicate, historical), PageRequest.of(0, 20), 3));
        when(resultRepository.findContributingSourceSha256s("draft")).thenReturn(List.of("first-sha"));
        when(resultRepository.findHistoricalContributingDocumentIds("draft")).thenReturn(List.of("historical"));

        EvaluationEligibleDocumentPageResponse page = eligibilityService.list("kb", "draft", 0, 20);

        assertThat(page.getContent()).extracting(value -> value.documentId() + ":" + value.eligible())
            .containsExactly("first:false", "second:false", "historical:false");
    }

    @Test
    void resolvesDraftListReferencesWithOneBoundedQueryPerWorkflowType() {
        SchemaDraftNode first = draft();
        SchemaDraftNode second = draft();
        second.setId("draft-2");
        List<String> draftIds = List.of("draft", "draft-2");
        when(sourceRepository.findActiveForDraftIds(draftIds)).thenReturn(List.of());
        when(analysisRepository.findCurrentForDraftIds(draftIds)).thenReturn(List.of());
        when(evaluationRepository.findLatestForDraftIds(draftIds)).thenReturn(List.of());
        when(reprocessingRepository.findLatestForDraftIds(draftIds)).thenReturn(List.of());

        assertThat(service.references(List.of(first, second))).containsOnlyKeys("draft", "draft-2");

        verify(sourceRepository).findActiveForDraftIds(draftIds);
        verify(analysisRepository).findCurrentForDraftIds(draftIds);
        verify(evaluationRepository).findLatestForDraftIds(draftIds);
        verify(reprocessingRepository).findLatestForDraftIds(draftIds);
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

    private DocumentUploadNode document(String id) {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId(id);
        document.setKnowledgeBaseId("kb");
        document.setOriginalFilename(id + ".txt");
        document.setContentType("text/plain");
        document.setSha256(id + "-sha");
        document.setUploadedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return document;
    }
}
