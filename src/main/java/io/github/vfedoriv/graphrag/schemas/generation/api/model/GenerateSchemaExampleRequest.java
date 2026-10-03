package io.github.vfedoriv.graphrag.schemas.generation.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GenerateSchemaExampleRequest(
    @Schema(
        description = "Unstructured input text used to generate a domain example.",
        example = "Acme signed contract C-101 with Beta Corp on 2025-01-11 for software maintenance."
    )
    @NotBlank String text,
    @Schema(
        description = "Optional additional instruction about domain, entity names, relations, or properties.",
        example = "Use Party and Contract entities; include signedDate property when available."
    )
    String userPrompt
) {
}
