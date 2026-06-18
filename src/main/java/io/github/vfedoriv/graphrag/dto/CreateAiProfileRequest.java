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
    @Min(1) int embeddingDimensions,
    @Min(1) Integer timeoutSeconds,
    @Min(0) Integer maxRetries,
    Boolean defaultProfile
) {
}
