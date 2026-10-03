package io.github.vfedoriv.graphrag.ai.domain;

public record EmbeddingSpace(
    String id,
    String normalizedBaseUrl,
    String model,
    int dimensions,
    String tokenizerId
) {
}
