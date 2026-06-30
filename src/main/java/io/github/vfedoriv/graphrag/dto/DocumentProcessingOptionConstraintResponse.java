package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DocumentProcessingOptionConstraintResponse(
    @Schema(description = "Minimum numeric value, when applicable.")
    Number min,
    @Schema(description = "Maximum numeric value, when applicable.")
    Number max,
    @Schema(description = "Allowed string values, when applicable.")
    List<String> allowedValues
) {
}
