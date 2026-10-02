package io.github.vfedoriv.graphrag.schemas.evaluation.application;

import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationEligibleDocumentPageResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationEligibleDocumentResponse;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationIneligibilityReason;
import io.github.vfedoriv.graphrag.schemas.evaluation.api.model.SchemaDraftEvaluationDtos.EvaluationReadiness;
import io.github.vfedoriv.graphrag.error.ConflictException;
import java.util.Set;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftAdmissions;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftContributors;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.EvaluationDocuments;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Service
public class SchemaDraftEvaluationEligibilityService {
    private final DraftAdmissions admissions;
    private final EvaluationDocuments documents;
    private final DraftContributors contributors;

    public SchemaDraftEvaluationEligibilityService(
        DraftAdmissions admissions,
        EvaluationDocuments documents,
        DraftContributors contributors
    ) {
        this.admissions = admissions;
        this.documents = documents;
        this.contributors = contributors;
    }

    @RelationalTransactional(readOnly = true)
    public EvaluationEligibleDocumentPageResponse list(
        String knowledgeBaseId, String draftId, int page, int size
    ) {
        DraftAdmissions.Draft draft = admissions.requireOwned(knowledgeBaseId, draftId);
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        EvaluationDocuments.DocumentPage documentPage = documents.listOwned(knowledgeBaseId, boundedPage, boundedSize);
        EligibilitySnapshot eligibility = resolve(draft);
        return new EvaluationEligibleDocumentPageResponse(
            draft.revision(), draft.aggregateRevisionId(), eligibility.readiness(), eligibility.blockingReason(),
            boundedPage, boundedSize, documentPage.totalElements(),
            documentPage.documents().stream().map(document -> response(document, eligibility)).toList());
    }

    public EligibilitySnapshot resolve(DraftAdmissions.Draft draft) {
        if (draft.aggregateRevisionId() == null || draft.aggregateRevisionId().isBlank()) {
            return new EligibilitySnapshot(
                EvaluationReadiness.NOT_READY, EvaluationIneligibilityReason.DRAFT_ANALYSIS_REQUIRED,
                Set.of(), Set.of());
        }
        DraftContributors.Contributors evidence = contributors.contributors(draft.knowledgeBaseId(), draft.id());
        return new EligibilitySnapshot(EvaluationReadiness.READY, null, evidence.sha256s(), evidence.historicalDocumentIds());
    }

    public void requireReady(EligibilitySnapshot eligibility) {
        if (eligibility.readiness() != EvaluationReadiness.READY) {
            throw new ConflictException("Schema draft analysis is required before held-out evaluation can start");
        }
    }

    public boolean isEligible(EvaluationDocuments.Metadata document, EligibilitySnapshot eligibility) {
        return eligibility.readiness() == EvaluationReadiness.READY
            && !eligibility.contributingSha256s().contains(document.sha256())
            && !eligibility.historicalDocumentIds().contains(document.documentId());
    }

    private EvaluationEligibleDocumentResponse response(EvaluationDocuments.Metadata document, EligibilitySnapshot eligibility) {
        boolean eligible = isEligible(document, eligibility);
        EvaluationIneligibilityReason reason = eligibility.readiness() == EvaluationReadiness.READY
            ? EvaluationIneligibilityReason.ACTIVE_DISCOVERY_EVIDENCE
            : eligibility.blockingReason();
        return new EvaluationEligibleDocumentResponse(
            document.documentId(), document.filename(), document.contentType(), document.sizeBytes(),
            document.sha256(), document.uploadedAt(), eligible,
            eligible ? null : reason);
    }

    public record EligibilitySnapshot(
        EvaluationReadiness readiness, EvaluationIneligibilityReason blockingReason,
        Set<String> contributingSha256s, Set<String> historicalDocumentIds
    ) {
        public EligibilitySnapshot {
            contributingSha256s = Set.copyOf(contributingSha256s);
            historicalDocumentIds = Set.copyOf(historicalDocumentIds);
        }
    }
}
