package io.github.vfedoriv.graphrag.schemas.evaluation.application;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.EvaluationDocuments;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationEligibleDocumentPageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
class SchemaDraftEvaluationEligibilityServiceTest {
    @Test
    void eligibilityUsesOnlySuccessfulContributorsFromTheCurrentAggregate() {
        DraftAdmissions lifecycleService = mock(DraftAdmissions.class);
        EvaluationDocuments documentRepository = mock(EvaluationDocuments.class);
        DraftContributors resultRepository = mock(DraftContributors.class);
        SchemaDraftEvaluationEligibilityService eligibilityService = new SchemaDraftEvaluationEligibilityService(
            lifecycleService, documentRepository, resultRepository);
        DraftAdmissions.Draft draft = draft("aggregate");
        EvaluationDocuments.Metadata evidence = document("evidence");
        EvaluationDocuments.Metadata heldOut = document("held-out");
        when(lifecycleService.requireOwned("kb", "draft")).thenReturn(draft);
        when(documentRepository.listOwned("kb", 0, 20))
            .thenReturn(new EvaluationDocuments.DocumentPage(0, 20, 2, 1, List.of(evidence, heldOut)));
        when(resultRepository.contributors("kb", "draft")).thenReturn(new DraftContributors.Contributors(Set.of("evidence-sha"), Set.of()));

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
        DraftAdmissions lifecycleService = mock(DraftAdmissions.class);
        EvaluationDocuments documentRepository = mock(EvaluationDocuments.class);
        DraftContributors resultRepository = mock(DraftContributors.class);
        SchemaDraftEvaluationEligibilityService eligibilityService = new SchemaDraftEvaluationEligibilityService(
            lifecycleService, documentRepository, resultRepository);
        DraftAdmissions.Draft draft = draft("aggregate");
        draft = draft(null);
        when(lifecycleService.requireOwned("kb", "draft")).thenReturn(draft);
        when(documentRepository.listOwned("kb", 1, 1))
            .thenReturn(new EvaluationDocuments.DocumentPage(1, 1, 3, 3, List.of(document("candidate"))));

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
        DraftAdmissions lifecycleService = mock(DraftAdmissions.class);
        EvaluationDocuments documentRepository = mock(EvaluationDocuments.class);
        DraftContributors resultRepository = mock(DraftContributors.class);
        SchemaDraftEvaluationEligibilityService eligibilityService = new SchemaDraftEvaluationEligibilityService(
            lifecycleService, documentRepository, resultRepository);
        DraftAdmissions.Draft draft = draft("aggregate");
        EvaluationDocuments.Metadata firstDuplicate = document("first");
        EvaluationDocuments.Metadata secondDuplicate = document("second");
        secondDuplicate = new EvaluationDocuments.Metadata("second", "second.txt", "text/plain", 0, firstDuplicate.sha256(), firstDuplicate.uploadedAt());
        EvaluationDocuments.Metadata historical = document("historical");
        when(lifecycleService.requireOwned("kb", "draft")).thenReturn(draft);
        when(documentRepository.listOwned("kb", 0, 20))
            .thenReturn(new EvaluationDocuments.DocumentPage(0, 20, 3, 1, List.of(firstDuplicate, secondDuplicate, historical)));
        when(resultRepository.contributors("kb", "draft")).thenReturn(new DraftContributors.Contributors(Set.of("first-sha"), Set.of("historical")));

        EvaluationEligibleDocumentPageResponse page = eligibilityService.list("kb", "draft", 0, 20);

        assertThat(page.getContent()).extracting(value -> value.documentId() + ":" + value.eligible())
            .containsExactly("first:false", "second:false", "historical:false");
    }

    private DraftAdmissions.Draft draft(String aggregateId) {
        return new DraftAdmissions.Draft("draft", "kb", 2, null, "target", 1, aggregateId, "{}", true, null, null);
    }
    private EvaluationDocuments.Metadata document(String id) {
        return new EvaluationDocuments.Metadata(id, id + ".txt", "text/plain", 0, id + "-sha", Instant.parse("2026-01-01T00:00:00Z"));
    }
}
