package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record GenerateSchemaResponse(
    @Schema(
        description = "Generated schema in YAML format.",
        example = "name: generated-legal-schema\nversion: 1\nnodes:\n  - label: Party\n    key: id\nrelationships: []"
    )
    String content,
    @Schema(description = "Saved schema identifier when save=true; null otherwise.", example = "schema-01")
    String schemaId
) {
}
