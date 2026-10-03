package io.github.vfedoriv.graphrag.ai.profiles.api.model;

import io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode;

import java.time.Instant;

public record AiProfileResponse(
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
    public AiProfileResponse {
        structuredOutputMode = structuredOutputMode == null ? StructuredOutputMode.PORTABLE : structuredOutputMode;
    }

    public AiProfileResponse(String id, String name, String baseUrl, String chatModel, String embeddingModel, String tokenizerId, String resolvedTokenizerId, int embeddingDimensions, int timeoutSeconds, int maxRetries, boolean defaultProfile, long revision, boolean apiKeyConfigured, String apiKeyMask, Instant createdAt, Instant updatedAt) {
        this(id, name, baseUrl, chatModel, embeddingModel, tokenizerId, resolvedTokenizerId, embeddingDimensions, timeoutSeconds, maxRetries, defaultProfile, revision, apiKeyConfigured, apiKeyMask, createdAt, updatedAt, StructuredOutputMode.PORTABLE);
    }

    public AiProfileResponse(
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
        this(
            id,
            name,
            baseUrl,
            chatModel,
            embeddingModel,
            null,
            null,
            embeddingDimensions,
            timeoutSeconds,
            maxRetries,
            defaultProfile,
            revision,
            apiKeyConfigured,
            apiKeyMask,
            createdAt,
            updatedAt
        );
    }
    public static AiProfileResponse from(io.github.vfedoriv.graphrag.ai.contracts.ProfileView view) {
        return new AiProfileResponse(view.id(), view.name(), view.baseUrl(), view.chatModel(), view.embeddingModel(), view.tokenizerId(), view.resolvedTokenizerId(), view.embeddingDimensions(), view.timeoutSeconds(), view.maxRetries(), view.defaultProfile(), view.revision(), view.apiKeyConfigured(), view.apiKeyMask(), view.createdAt(), view.updatedAt(), view.structuredOutputMode());
    }

}
