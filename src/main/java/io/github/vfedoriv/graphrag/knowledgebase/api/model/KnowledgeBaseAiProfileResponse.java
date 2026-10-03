package io.github.vfedoriv.graphrag.knowledgebase.api.model;

import java.time.Instant;

@io.swagger.v3.oas.annotations.media.Schema(name = "AiProfileResponse")
public record KnowledgeBaseAiProfileResponse(
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
) {
    public KnowledgeBaseAiProfileResponse(
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
    public static KnowledgeBaseAiProfileResponse from(io.github.vfedoriv.graphrag.ai.contracts.ProfileView view) {
        return new KnowledgeBaseAiProfileResponse(view.id(), view.name(), view.baseUrl(), view.chatModel(), view.embeddingModel(), view.tokenizerId(), view.resolvedTokenizerId(), view.embeddingDimensions(), view.timeoutSeconds(), view.maxRetries(), view.defaultProfile(), view.revision(), view.apiKeyConfigured(), view.apiKeyMask(), view.createdAt(), view.updatedAt());
    }

}
