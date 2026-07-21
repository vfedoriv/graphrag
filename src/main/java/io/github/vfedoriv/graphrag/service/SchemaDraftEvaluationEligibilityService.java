package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationEligibleDocumentPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationEligibleDocumentResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationIneligibilityReason;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationReadiness;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceResultRepository;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchemaDraftEvaluationEligibilityService {
    private final SchemaDraftLifecycleService lifecycleService;
    private final DocumentUploadRepository documentRepository;
    private final SchemaDraftSourceResultRepository resultRepository;

    public SchemaDraftEvaluationEligibilityService(
        SchemaDraftLifecycleService lifecycleService,
        DocumentUploadRepository documentRepository,
        SchemaDraftSourceResultRepository resultRepository
    ) {
        this.lifecycleService = lifecycleService;
        this.documentRepository = documentRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional(readOnly = true)
    public EvaluationEligibleDocumentPageResponse list(
        String knowledgeBaseId, String draftId, int page, int size
    ) {
        SchemaDraftNode draft = lifecycleService.requireOwned(knowledgeBaseId, draftId);
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        Page<DocumentUploadNode> documents = documentRepository.findPageByKnowledgeBaseId(
            knowledgeBaseId, PageRequest.of(boundedPage, boundedSize));
        EligibilitySnapshot eligibility = resolve(draft);
        return new EvaluationEligibleDocumentPageResponse(
            draft.getRevision(), draft.getCurrentAggregateId(), eligibility.readiness(), eligibility.blockingReason(),
            boundedPage, boundedSize, documents.getTotalElements(),
            documents.getContent().stream().map(document -> response(document, eligibility)).toList());
    }

    @Transactional(readOnly = true)
    public EligibilitySnapshot resolve(SchemaDraftNode draft) {
        if (draft.getCurrentAggregateId() == null || draft.getCurrentAggregateId().isBlank()) {
            return new EligibilitySnapshot(
                EvaluationReadiness.NOT_READY, EvaluationIneligibilityReason.DRAFT_ANALYSIS_REQUIRED,
                Set.of(), Set.of());
        }
        return new EligibilitySnapshot(
            EvaluationReadiness.READY, null,
            Set.copyOf(resultRepository.findContributingSourceSha256s(draft.getId())),
            Set.copyOf(resultRepository.findHistoricalContributingDocumentIds(draft.getId())));
    }

    public void requireReady(EligibilitySnapshot eligibility) {
        if (eligibility.readiness() != EvaluationReadiness.READY) {
            throw new ConflictException("Schema draft analysis is required before held-out evaluation can start");
        }
    }

    public boolean isEligible(DocumentUploadNode document, EligibilitySnapshot eligibility) {
        return eligibility.readiness() == EvaluationReadiness.READY
            && !eligibility.contributingSha256s().contains(document.getSha256())
            && !eligibility.historicalDocumentIds().contains(document.getId());
    }

    private EvaluationEligibleDocumentResponse response(DocumentUploadNode document, EligibilitySnapshot eligibility) {
        boolean eligible = isEligible(document, eligibility);
        EvaluationIneligibilityReason reason = eligibility.readiness() == EvaluationReadiness.READY
            ? EvaluationIneligibilityReason.ACTIVE_DISCOVERY_EVIDENCE
            : eligibility.blockingReason();
        return new EvaluationEligibleDocumentResponse(
            document.getId(), document.getOriginalFilename(), document.getContentType(), document.getSizeBytes(),
            document.getSha256(), document.getUploadedAt(), eligible,
            eligible ? null : reason);
    }

    public record EligibilitySnapshot(
        EvaluationReadiness readiness, EvaluationIneligibilityReason blockingReason,
        Set<String> contributingSha256s, Set<String> historicalDocumentIds
    ) { }
}
