package io.github.vfedoriv.graphrag.ai.configuration;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.model")
public record ModelProperties(
    @NotBlank String baseUrl,
    String apiKey,
    @NotBlank String embeddingModel,
    @Min(1) int embeddingDimensions,
    @NotBlank String chatModel
) {
}
