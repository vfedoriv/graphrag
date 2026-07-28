package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationState;
import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationType;
import io.github.vfedoriv.graphrag.repository.DocumentStorageMutationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Service
public class DocumentStorageMutationService {
    private final DocumentStorageMutationRepository mutationRepository;

    public DocumentStorageMutationService(DocumentStorageMutationRepository mutationRepository) {
        this.mutationRepository = mutationRepository;
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentStorageMutationNode begin(DocumentStorageMutationType type, String knowledgeBaseId, String documentId, String contentUri, String previousContentUri) {
        Instant now = Instant.now();
        DocumentStorageMutationNode mutation = new DocumentStorageMutationNode();
        mutation.setId(UUID.randomUUID().toString());
        mutation.setType(type);
        mutation.setState(DocumentStorageMutationState.PENDING);
        mutation.setKnowledgeBaseId(knowledgeBaseId);
        mutation.setDocumentId(documentId);
        mutation.setContentUri(contentUri);
        mutation.setPreviousContentUri(previousContentUri);
        mutation.setCreatedAt(now);
        mutation.setUpdatedAt(now);
        return mutationRepository.save(mutation);
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStoredContent(String mutationId, String contentUri) {
        DocumentStorageMutationNode mutation = mutationRepository.findById(mutationId).orElseThrow();
        mutation.setContentUri(contentUri);
        mutation.setUpdatedAt(Instant.now());
        mutationRepository.save(mutation);
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String mutationId) { transition(mutationId, DocumentStorageMutationState.COMPLETED, null); }
    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(String mutationId) { transition(mutationId, DocumentStorageMutationState.COMPENSATED, null); }
    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String mutationId, Exception exception) {
        DocumentStorageMutationNode mutation = mutationRepository.findById(mutationId).orElseThrow();
        mutation.setRetryCount(mutation.getRetryCount() + 1);
        mutation.setLastError(safeMessage(exception));
        mutation.setUpdatedAt(Instant.now());
        mutation.setClaimedBy(null);
        mutation.setClaimUntil(null);
        mutationRepository.save(mutation);
    }
    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public void markPermanentlyFailed(String mutationId, Exception exception) { transition(mutationId, DocumentStorageMutationState.FAILED, safeMessage(exception)); }

    private void transition(String mutationId, DocumentStorageMutationState state, String error) {
        DocumentStorageMutationNode mutation = mutationRepository.findById(mutationId).orElseThrow();
        Instant now = Instant.now();
        mutation.setState(state);
        mutation.setLastError(error);
        mutation.setUpdatedAt(now);
        mutation.setCompletedAt(now);
        mutation.setClaimedBy(null);
        mutation.setClaimUntil(null);
        mutationRepository.save(mutation);
    }

    private String safeMessage(Exception exception) {
        return exception == null ? null : exception.getClass().getSimpleName();
    }
}
