package io.github.vfedoriv.graphrag.dto;

import java.util.Map;

public record GeneratedQueryResponse(
    String cypher,
    String explanation,
    Map<String, Object> parameters,
    QueryValidationResponse validation
) {
}
