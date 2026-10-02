package io.github.vfedoriv.graphrag.search.query.api.model;

import io.github.vfedoriv.graphrag.search.query.api.model.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryExecutionResponse;

public record QueryAskResponse(
    @io.swagger.v3.oas.annotations.media.Schema(description = "Cypher generation output.")
    GeneratedQueryResponse generatedQuery,
    @io.swagger.v3.oas.annotations.media.Schema(description = "Cypher execution output.")
    QueryExecutionResponse execution
) {
}
