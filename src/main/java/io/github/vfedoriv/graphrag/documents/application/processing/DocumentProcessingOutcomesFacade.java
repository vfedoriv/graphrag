package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentProcessingOutcomes;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import org.springframework.stereotype.Service;

/** Transitional documents-owned bridge to stored processing outcomes. */
@Service
public class DocumentProcessingOutcomesFacade implements DocumentProcessingOutcomes {
    private final DocumentProcessingRunRepository runs;

    public DocumentProcessingOutcomesFacade(DocumentProcessingRunRepository runs) {
        this.runs = runs;
    }

    @Override
    public boolean completedOverwrite(Request request) {
        return runs.findByDocumentIdOrderByStartedAtAsc(request.documentId()).stream()
            .filter(run -> run.getStatus() == DocumentProcessingRunStatus.COMPLETED)
            .filter(DocumentProcessingRunNode::isActiveCompleted)
            .filter(run -> request.expectedSourceSha256().equals(run.getSourceSha256()))
            .filter(run -> request.requiredChunkerRevision() == null
                || request.requiredChunkerRevision().equals(run.getEffectiveChunkerRevision()))
            .anyMatch(run -> request.itemStartedAt() == null || !run.getStartedAt().isBefore(request.itemStartedAt()));
    }
}
