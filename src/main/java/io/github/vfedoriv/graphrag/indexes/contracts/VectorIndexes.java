package io.github.vfedoriv.graphrag.indexes.contracts;

import io.github.vfedoriv.graphrag.ai.domain.EmbeddingSpace;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;

/** Shared vector index identity and scoped membership mechanics. */
public interface VectorIndexes {
    String indexName(String knowledgeBaseId, String embeddingSpaceId);
    String labelName(String knowledgeBaseId, String embeddingSpaceId);
    void ensureIndex(String knowledgeBaseId, EmbeddingTarget embeddingSpace);
    void assignChunk(String chunkId, String knowledgeBaseId, EmbeddingTarget embeddingSpace);
    long managedIndexCount();

    default void ensureIndex(String knowledgeBaseId, EmbeddingSpace embeddingSpace) {
        ensureIndex(knowledgeBaseId, new EmbeddingTarget(embeddingSpace.id(), embeddingSpace.normalizedBaseUrl(),
            embeddingSpace.model(), embeddingSpace.dimensions(), embeddingSpace.tokenizerId()));
    }

    default void assignChunk(String chunkId, String knowledgeBaseId, EmbeddingSpace embeddingSpace) {
        assignChunk(chunkId, knowledgeBaseId, new EmbeddingTarget(embeddingSpace.id(), embeddingSpace.normalizedBaseUrl(),
            embeddingSpace.model(), embeddingSpace.dimensions(), embeddingSpace.tokenizerId()));
    }
}
