package io.github.vfedoriv.graphrag.settings.configuration;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.extraction")
public record ExtractionProperties(
    @Min(1) int maxEntitiesPerChunk,
    @Min(1) int maxRelationshipsPerChunk,
    @Min(0) int maxRetries
) {
}
