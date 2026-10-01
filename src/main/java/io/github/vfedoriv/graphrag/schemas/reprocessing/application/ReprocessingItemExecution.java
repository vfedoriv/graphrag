package io.github.vfedoriv.graphrag.schemas.reprocessing.application;

import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentExecutor;
import org.springframework.stereotype.Component;

/** Port-only item execution; claims, retry decisions and completion remain schema-owned. */
@Component
public class ReprocessingItemExecution {
    private final ReprocessingDocumentExecutor documents;

    public ReprocessingItemExecution(ReprocessingDocumentExecutor documents) {
        this.documents = documents;
    }

    public boolean sourceMatches(ReprocessingDocumentExecutor.Source source) {
        return documents.sourceMatches(source);
    }

    public ReprocessingDocumentExecutor.Result execute(ReprocessingDocumentExecutor.Request request) {
        try {
            return documents.execute(request);
        } catch (Exception exception) {
            return new ReprocessingDocumentExecutor.Result(
                ReprocessingDocumentExecutor.Status.FAILED, exception.getClass().getSimpleName());
        }
    }
}
