package io.github.vfedoriv.graphrag.error;

public class KnowledgeBaseNotEmptyException extends ConflictException {
    private final long remainingDocumentCount;

    public KnowledgeBaseNotEmptyException(String knowledgeBaseId, long remainingDocumentCount) {
        super("Knowledge base contains " + remainingDocumentCount + " document(s): " + knowledgeBaseId);
        this.remainingDocumentCount = remainingDocumentCount;
    }

    public long getRemainingDocumentCount() { return remainingDocumentCount; }
}
