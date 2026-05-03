package io.github.vfedoriv.graphrag.query;

import java.util.Map;

public record GeneratedCypher(
    String cypher,
    String explanation,
    Map<String, Object> parameters
) {
}
