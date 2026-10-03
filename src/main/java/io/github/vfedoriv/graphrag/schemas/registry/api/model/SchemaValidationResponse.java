package io.github.vfedoriv.graphrag.schemas.registry.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SchemaValidationResponse(
    @Schema(description = "Whether JSON content passed validation.", example = "true")
    boolean valid,
    @Schema(description = "Validation errors when valid=false.", example = "[]")
    List<String> errors
) {
}
