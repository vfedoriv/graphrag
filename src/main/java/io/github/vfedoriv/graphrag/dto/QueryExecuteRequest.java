package io.github.vfedoriv.graphrag.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record QueryExecuteRequest(
    @NotBlank String cypher,
    Map<String, Object> parameters
) {
}
