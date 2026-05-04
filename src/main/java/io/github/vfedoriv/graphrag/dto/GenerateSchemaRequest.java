package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record GenerateSchemaRequest(
    @Schema(description = "Schema logical name.", example = "generated-legal-schema")
    @NotBlank String name,
    @Schema(description = "Schema version.", example = "1")
    @Min(1) int version,
    @Schema(description = "Schema description.", example = "Generated from legal contracts corpus.")
    String description,
    @Schema(
        description = "Unstructured input text used to infer graph schema.",
        example = "Acme signed contract C-101 with Beta Corp on 2025-01-11 for software maintenance."
    )
    @NotBlank String text,
    @Schema(description = "When true, saves generated schema in registry.", example = "false")
    Boolean save
) {
}
