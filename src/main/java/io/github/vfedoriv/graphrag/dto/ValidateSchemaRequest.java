package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ValidateSchemaRequest(
    @Schema(
        description = "Schema YAML content to validate.",
        example = "name: legal-contracts\nversion: 1\nnodes:\n  - label: Party\n    key: partyId\nrelationships: []"
    )
    @NotBlank String content
) {
}
