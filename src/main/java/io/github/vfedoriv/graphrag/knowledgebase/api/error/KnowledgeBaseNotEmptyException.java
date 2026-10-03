package io.github.vfedoriv.graphrag.knowledgebase.api.error;

import io.github.vfedoriv.graphrag.http.contracts.ConflictException;

public class KnowledgeBaseNotEmptyException extends ConflictException {
    private final long remainingDocumentCount;

    public KnowledgeBaseNotEmptyException(String knowledgeBaseId, long remainingDocumentCount) {
        super("Knowledge base contains " + remainingDocumentCount + " document(s): " + knowledgeBaseId);
        this.remainingDocumentCount = remainingDocumentCount;
    }

    public long getRemainingDocumentCount() { return remainingDocumentCount; }
}
