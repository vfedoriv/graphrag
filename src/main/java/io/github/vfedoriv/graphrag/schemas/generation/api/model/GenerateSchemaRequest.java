package io.github.vfedoriv.graphrag.schemas.generation.api.model;

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
    @Schema(
        description = "Example entities and relationships for the target domain used to guide extraction.",
        example = "[{\"head\":\"Acme\",\"head_type\":\"Party\",\"relation\":\"SIGNED\",\"tail\":\"Contract C-101\",\"tail_type\":\"Contract\"}]"
    )
    @NotBlank String example
) {
}
