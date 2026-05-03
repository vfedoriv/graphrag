package io.github.vfedoriv.graphrag.query;

import java.util.List;
import java.util.Map;

public record QueryValidationResult(
    boolean valid,
    String cypher,
    Map<String, Object> parameters,
    List<String> errors
) {
}
