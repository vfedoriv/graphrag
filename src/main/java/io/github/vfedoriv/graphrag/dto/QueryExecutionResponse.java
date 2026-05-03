package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public record QueryExecutionResponse(
    @Schema(description = "Executed Cypher query.", example = "MATCH (c:Contract) RETURN c.title LIMIT 10")
    String cypher,
    @Schema(description = "Named parameters applied to query execution.", example = "{\"status\":\"ACTIVE\"}")
    Map<String, Object> parameters,
    @Schema(description = "Validation metadata applied before execution.")
    QueryValidationResponse validation,
    @Schema(description = "Returned column names.", example = "[\"title\"]")
    List<String> columns,
    @Schema(description = "Result rows as name/value maps.", example = "[{\"title\":\"Master Service Agreement\"}]")
    List<Map<String, Object>> rows,
    @Schema(description = "Number of rows returned.", example = "1")
    int rowCount,
    @Schema(description = "Execution time in milliseconds.", example = "23")
    long executionTimeMs
) {
}
