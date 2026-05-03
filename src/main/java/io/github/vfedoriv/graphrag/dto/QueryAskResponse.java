package io.github.vfedoriv.graphrag.dto;

public record QueryAskResponse(
    @io.swagger.v3.oas.annotations.media.Schema(description = "Cypher generation output.")
    GeneratedQueryResponse generatedQuery,
    @io.swagger.v3.oas.annotations.media.Schema(description = "Cypher execution output.")
    QueryExecutionResponse execution
) {
}
