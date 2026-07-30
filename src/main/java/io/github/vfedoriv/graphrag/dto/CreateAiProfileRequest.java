package io.github.vfedoriv.graphrag.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateAiProfileRequest(
    @NotBlank String id,
    @NotBlank String name,
    @NotBlank String baseUrl,
    String apiKey,
    @NotBlank String chatModel,
    @NotBlank String embeddingModel,
    String tokenizerId,
    @Min(1) int embeddingDimensions,
    @Min(1) Integer timeoutSeconds,
    @Min(0) Integer maxRetries,
    Boolean defaultProfile
) {
    public CreateAiProfileRequest(
        String id,
        String name,
        String baseUrl,
        String apiKey,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        Integer timeoutSeconds,
        Integer maxRetries,
        Boolean defaultProfile
    ) {
        this(
            id,
            name,
            baseUrl,
            apiKey,
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
