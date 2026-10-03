package io.github.vfedoriv.graphrag.ai.application;

import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingCompatibilityRule;
import io.github.vfedoriv.graphrag.ai.ports.StoredEmbeddingInformation;
import io.github.vfedoriv.graphrag.ai.api.error.EmbeddingSpaceConflictException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingCompatibility {
    private final StoredEmbeddingInformation stored;

    public EmbeddingCompatibility(StoredEmbeddingInformation stored) {
        this.stored = Objects.requireNonNull(stored);
    }

    public boolean hasEmbeddedChunks(String knowledgeBaseId) {
        return !stored.observations(knowledgeBaseId).isEmpty();
    }

    public void requireCompatible(String knowledgeBaseId, EmbeddingTarget target) {
        if (!EmbeddingCompatibilityRule.compatible(target, stored.observations(knowledgeBaseId))) {
            throw new EmbeddingSpaceConflictException(
                "AI profile embedding space is incompatible with stored embeddings in knowledge base "
                    + knowledgeBaseId + ". Re-embed legacy or incompatible chunks before changing the profile.",
                List.of(knowledgeBaseId));
        }
    }

    public List<String> incompatibleKnowledgeBaseIds(List<String> knowledgeBaseIds, EmbeddingTarget target) {
        return knowledgeBaseIds.stream().filter(id ->
            !EmbeddingCompatibilityRule.compatible(target, stored.observations(id))).toList();
    }
}
