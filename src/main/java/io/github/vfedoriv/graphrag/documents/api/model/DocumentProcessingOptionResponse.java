package io.github.vfedoriv.graphrag.documents.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DocumentProcessingOptionResponse(
    @Schema(description = "Stable option key.", example = "ocrEnabled")
    String key,
    @Schema(description = "Human-readable label.", example = "OCR enabled")
    String label,
    @Schema(description = "Option value type.", example = "BOOLEAN")
    String valueType,
    @Schema(description = "Built-in default value.")
    Object defaultValue,
    @Schema(description = "Saved document default value, when present.")
    Object savedDefaultValue,
    @Schema(description = "Validation constraints.")
    DocumentProcessingOptionConstraintResponse constraints,
    @Schema(description = "Applicable parser identifiers.")
    List<String> parserIds,
    @Schema(description = "Applicable file formats.")
    List<String> fileFormats,
    @Schema(description = "Whether clients may set this option.")
    boolean mutable,
    @Schema(description = "Option description.")
    String description
) {
}
