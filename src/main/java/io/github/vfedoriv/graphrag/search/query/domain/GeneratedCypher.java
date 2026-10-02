package io.github.vfedoriv.graphrag.search.query.domain;

import java.util.Map;

public record GeneratedCypher(
    String cypher,
    String explanation,
    Map<String, Object> parameters
) {
}
