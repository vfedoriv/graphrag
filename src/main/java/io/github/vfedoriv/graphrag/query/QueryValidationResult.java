package io.github.vfedoriv.graphrag.query;

import java.util.List;
import java.util.Map;

public record QueryValidationResult(
    boolean valid,
    String cypher,
    Map<String, Object> parameters,
    List<String> errors,
    QueryPolicy policy
) {

    public QueryValidationResult(
        boolean valid,
        String cypher,
        Map<String, Object> parameters,
        List<String> errors
    ) {
        this(valid, cypher, parameters, errors, new QueryPolicy(200, java.time.Duration.ofSeconds(15), true, List.of()));
    }
}
