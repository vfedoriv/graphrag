package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record GenerateSchemaExampleResponse(
    @Schema(
        description = "Generated domain example for schema extraction.",
        example = "[{\"head\":\"Acme\",\"head_type\":\"Party\",\"relation\":\"SIGNED\",\"tail\":\"Contract C-101\",\"tail_type\":\"Contract\"}]"
    )
    String example
) {
}
