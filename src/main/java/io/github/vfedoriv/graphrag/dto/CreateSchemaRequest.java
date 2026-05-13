package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateSchemaRequest(
    @Schema(
        description = "Schema definition in YAML format.",
        example = "name: legal-contracts\nversion: 1\nnodes:\n  - label: Contract\n    key: contractId\nrelationships: []"
    )
    @NotBlank String content,
    @Schema(description = "Schema source classification.", example = "GENERATED")
    SchemaSourceType sourceType
) {
}
