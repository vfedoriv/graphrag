package io.github.vfedoriv.graphrag.ai.contracts;

import io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode;

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
    Instant updatedAt,
    StructuredOutputMode structuredOutputMode
) {
    public ProfileView {
        structuredOutputMode = structuredOutputMode == null ? StructuredOutputMode.PORTABLE : structuredOutputMode;
    }

    public ProfileView(String id, String name, String baseUrl, String chatModel, String embeddingModel, String tokenizerId, String resolvedTokenizerId, int embeddingDimensions, int timeoutSeconds, int maxRetries, boolean defaultProfile, long revision, boolean apiKeyConfigured, String apiKeyMask, Instant createdAt, Instant updatedAt) {
        this(id, name, baseUrl, chatModel, embeddingModel, tokenizerId, resolvedTokenizerId, embeddingDimensions, timeoutSeconds, maxRetries, defaultProfile, revision, apiKeyConfigured, apiKeyMask, createdAt, updatedAt, StructuredOutputMode.PORTABLE);
    }
 }
