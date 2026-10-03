package io.github.vfedoriv.graphrag.schemas.generation.api.model;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

public record GenerateSchemaExampleResponse(
    @JsonValue
    @Schema(
        description = "Generated domain example for schema extraction.",
        example = "[{\"head\":\"Acme\",\"head_type\":\"Party\",\"relation\":\"SIGNED\",\"tail\":\"Contract C-101\",\"tail_type\":\"Contract\"}]"
    )
    String value
) {
}
