package io.github.vfedoriv.graphrag.dto;

import java.time.Instant;

public record AiProfileResponse(
    String id,
    String name,
    String baseUrl,
    String chatModel,
    String embeddingModel,
    int embeddingDimensions,
    int timeoutSeconds,
    int maxRetries,
    boolean defaultProfile,
    long revision,
    boolean apiKeyConfigured,
    String apiKeyMask,
    Instant createdAt,
    Instant updatedAt
) {
}
