package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.documents.ports.DocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.documents.ports.ExtractionRunRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

@Component
public class DocumentRunHistoryLifecycle {
    private final ExtractionRunRepository extractionRunRepository;
    private final DocumentProcessingRunRepository processingRunRepository;

    public DocumentRunHistoryLifecycle(
        ExtractionRunRepository extractionRunRepository,
        DocumentProcessingRunRepository processingRunRepository
    ) {
        this.extractionRunRepository = extractionRunRepository;
        this.processingRunRepository = processingRunRepository;
    }

    @RelationalTransactional(propagation = Propagation.REQUIRES_NEW)
    public DeletedRunHistory deleteForReplacement(String documentId) {
        long extractionRuns = extractionRunRepository.deleteByDocumentId(documentId);
        long processingRuns = processingRunRepository.deleteByDocumentId(documentId);
        return new DeletedRunHistory(processingRuns, extractionRuns);
    }

    public record DeletedRunHistory(long processingRuns, long extractionRuns) {
    }
}
