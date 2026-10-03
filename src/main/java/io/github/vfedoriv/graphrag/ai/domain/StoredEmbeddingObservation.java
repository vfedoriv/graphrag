package io.github.vfedoriv.graphrag.ai.domain;

/** Raw stored fields; absent space IDs are deliberately not backfilled. */
public record StoredEmbeddingObservation(String embeddingSpaceId, String embeddingModel, String tokenizerId) { }
