package io.github.vfedoriv.graphrag.ai.profiles.api.model;

import io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateAiProfileRequest(
    @NotBlank String name,
    @NotBlank String baseUrl,
    String apiKey,
    Boolean clearApiKey,
    @NotBlank String chatModel,
    @NotBlank String embeddingModel,
    String tokenizerId,
    @Min(1) int embeddingDimensions,
    @Min(1) Integer timeoutSeconds,
    @Min(0) Integer maxRetries,
    Boolean defaultProfile,
    StructuredOutputMode structuredOutputMode
) {
    public UpdateAiProfileRequest(String name, String baseUrl, String apiKey, Boolean clearApiKey, String chatModel, String embeddingModel, String tokenizerId, int embeddingDimensions, Integer timeoutSeconds, Integer maxRetries, Boolean defaultProfile) {
        this(name, baseUrl, apiKey, clearApiKey, chatModel, embeddingModel, tokenizerId, embeddingDimensions, timeoutSeconds, maxRetries, defaultProfile, null);
    }

    public UpdateAiProfileRequest(
        String name,
        String baseUrl,
        String apiKey,
        Boolean clearApiKey,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        Integer timeoutSeconds,
        Integer maxRetries,
        Boolean defaultProfile
    ) {
        this(
            name,
            baseUrl,
            apiKey,
            clearApiKey,
            chatModel,
            embeddingModel,
            null,
            embeddingDimensions,
            timeoutSeconds,
            maxRetries,
            defaultProfile
        );
    }
}
