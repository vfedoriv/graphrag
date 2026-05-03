package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SchemaValidationResponse(
    @Schema(description = "Whether YAML content passed validation.", example = "true")
    boolean valid,
    @Schema(description = "Validation errors when valid=false.", example = "[]")
    List<String> errors
) {
}
