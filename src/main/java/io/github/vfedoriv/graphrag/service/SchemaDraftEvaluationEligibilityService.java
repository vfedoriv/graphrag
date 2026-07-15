package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationEligibleDocumentPageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationEligibleDocumentResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.EvaluationIneligibilityReason;
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
        Set<String> contributing = contributingDocumentIds(draftId);
        return new EvaluationEligibleDocumentPageResponse(
            draft.getRevision(), draft.getCurrentAggregateId(), boundedPage, boundedSize, documents.getTotalElements(),
            documents.getContent().stream().map(document -> response(document, contributing)).toList());
    }

    @Transactional(readOnly = true)
    public Set<String> contributingDocumentIds(String draftId) {
        return Set.copyOf(resultRepository.findContributingDocumentIds(draftId));
    }

    private EvaluationEligibleDocumentResponse response(DocumentUploadNode document, Set<String> contributing) {
        boolean eligible = !contributing.contains(document.getId());
        return new EvaluationEligibleDocumentResponse(
            document.getId(), document.getOriginalFilename(), document.getContentType(), document.getSizeBytes(),
            document.getSha256(), document.getUploadedAt(), eligible,
            eligible ? null : EvaluationIneligibilityReason.ACTIVE_DISCOVERY_EVIDENCE);
    }
}
