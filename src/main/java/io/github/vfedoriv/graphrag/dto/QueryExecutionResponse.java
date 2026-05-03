package io.github.vfedoriv.graphrag.dto;

import java.util.List;
import java.util.Map;

public record QueryExecutionResponse(
    String cypher,
    Map<String, Object> parameters,
    QueryValidationResponse validation,
    List<String> columns,
    List<Map<String, Object>> rows,
    int rowCount,
    long executionTimeMs
) {
}
