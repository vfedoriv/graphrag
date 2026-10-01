package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Transitional value-mapping bridge for processing, migration preparation, and search. */
@Service
public class EmbeddingSpacePolicy {
    private final EmbeddingCompatibility compatibility;

    public EmbeddingSpacePolicy(EmbeddingCompatibility compatibility) {
        this.compatibility = Objects.requireNonNull(compatibility);
    }

    public EmbeddingSpace spaceFor(AiProfileNode profile) {
        return EmbeddingSpaceIdentity.fromProfile(profile);
    }

    public void requireCompatible(String knowledgeBaseId, AiProfileNode profile) {
        requireCompatible(knowledgeBaseId, spaceFor(profile));
    }

    public boolean hasEmbeddedChunks(String knowledgeBaseId) {
        return compatibility.hasEmbeddedChunks(knowledgeBaseId);
    }

    public void requireCompatible(String knowledgeBaseId, EmbeddingSpace targetSpace) {
        compatibility.requireCompatible(knowledgeBaseId, target(targetSpace));
    }

    public List<String> incompatibleKnowledgeBaseIds(List<String> knowledgeBaseIds, EmbeddingSpace targetSpace) {
        return compatibility.incompatibleKnowledgeBaseIds(knowledgeBaseIds, target(targetSpace));
    }

    private EmbeddingTarget target(EmbeddingSpace space) {
        return new EmbeddingTarget(space.id(), space.normalizedBaseUrl(), space.model(), space.dimensions(), space.tokenizerId());
    }
}
