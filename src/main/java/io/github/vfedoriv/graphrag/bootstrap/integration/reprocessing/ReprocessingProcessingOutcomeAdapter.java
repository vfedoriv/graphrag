package io.github.vfedoriv.graphrag.bootstrap.integration.reprocessing;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentProcessingOutcomes;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingProcessingOutcomeReader;
import org.springframework.stereotype.Component;

@Component
public class ReprocessingProcessingOutcomeAdapter implements ReprocessingProcessingOutcomeReader {
    private final DocumentProcessingOutcomes documents;

    public ReprocessingProcessingOutcomeAdapter(DocumentProcessingOutcomes documents) {
        this.documents = documents;
    }

    @Override
    public boolean completedOverwrite(Request request) {
        return documents.completedOverwrite(new DocumentProcessingOutcomes.Request(
            request.documentId(), request.expectedSourceSha256(), request.requiredChunkerRevision(), request.itemStartedAt()));
    }
}
