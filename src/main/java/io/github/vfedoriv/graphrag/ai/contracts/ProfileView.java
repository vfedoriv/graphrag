package io.github.vfedoriv.graphrag.ai.contracts;

import java.time.Instant;

/** Non-secret profile management view for association APIs. */
public record ProfileView(
    String id,
    String name,
    String baseUrl,
    String chatModel,
    String embeddingModel,
    String tokenizerId,
    String resolvedTokenizerId,
    int embeddingDimensions,
    int timeoutSeconds,
    int maxRetries,
    boolean defaultProfile,
    long revision,
    boolean apiKeyConfigured,
    String apiKeyMask,
    Instant createdAt,
    Instant updatedAt
) { }
