package io.github.vfedoriv.graphrag.dto;

import java.util.List;
import java.util.Map;

public record QueryValidationResponse(
    boolean valid,
    String cypher,
    Map<String, Object> parameters,
    List<String> errors,
    int maxRows,
    int timeoutSeconds
) {
}
