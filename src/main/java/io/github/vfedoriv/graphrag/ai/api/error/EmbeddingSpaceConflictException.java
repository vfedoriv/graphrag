package io.github.vfedoriv.graphrag.ai.api.error;

import io.github.vfedoriv.graphrag.http.contracts.ConflictException;

import java.util.List;

public class EmbeddingSpaceConflictException extends ConflictException {
    private final List<String> affectedKnowledgeBaseIds;

    public EmbeddingSpaceConflictException(String message, List<String> affectedKnowledgeBaseIds) {
        super(message);
        this.affectedKnowledgeBaseIds = List.copyOf(affectedKnowledgeBaseIds);
    }

    public List<String> getAffectedKnowledgeBaseIds() {
        return affectedKnowledgeBaseIds;
    }
}
