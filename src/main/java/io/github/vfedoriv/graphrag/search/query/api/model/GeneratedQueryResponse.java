package io.github.vfedoriv.graphrag.search.query.api.model;

import io.github.vfedoriv.graphrag.search.query.api.model.QueryValidationResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record GeneratedQueryResponse(
    @Schema(description = "Generated Cypher query.", example = "MATCH (c:Contract) RETURN c.title LIMIT 10")
    String cypher,
    @Schema(description = "Model-produced explanation of the generated query.", example = "Returns the first 10 contract titles.")
    String explanation,
    @Schema(description = "Named Cypher parameters.", example = "{\"status\":\"ACTIVE\"}")
    Map<String, Object> parameters,
    @Schema(description = "Validation details for the generated query.")
    QueryValidationResponse validation
) {
}
