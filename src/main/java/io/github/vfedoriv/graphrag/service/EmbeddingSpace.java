package io.github.vfedoriv.graphrag.service;

public record EmbeddingSpace(String id, String normalizedBaseUrl, String model, int dimensions) {
}
