package io.github.vfedoriv.graphrag.dto;

public record QueryAskResponse(
    GeneratedQueryResponse generatedQuery,
    QueryExecutionResponse execution
) {
}
