package io.github.vfedoriv.graphrag.dto;

import java.util.List;

public record SchemaValidationResponse(
    boolean valid,
    List<String> errors
) {
}
