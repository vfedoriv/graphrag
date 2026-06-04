package io.github.vfedoriv.graphrag.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.ai.observability")
public record AiObservabilityProperties(
    boolean enabled,
    boolean contentCaptureEnabled,
    @Min(1) int maxAttributeLength,
    boolean inputOutputContentEnabled,
    @Min(1) int maxInputOutputLength,
    boolean modelNameTagEnabled,
    boolean schemaNameTagEnabled
) {

    public static AiObservabilityProperties disabled() {
        return new AiObservabilityProperties(false, false, 512, true, 1_048_576, true, true);
    }
}
