package io.github.vfedoriv.graphrag.search.query.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryPolicyResponse;
import java.util.List;
import java.util.Map;

public record QueryValidationResponse(
    @Schema(description = "Whether the query passed validation checks.", example = "true")
    boolean valid,
    @Schema(description = "Validated or normalized Cypher query.", example = "MATCH (c:Contract) RETURN c.title LIMIT 10")
    String cypher,
    @Schema(description = "Normalized parameters associated with the query.", example = "{\"status\":\"ACTIVE\"}")
    Map<String, Object> parameters,
    @Schema(description = "Validation errors when valid=false.", example = "[\"Blocked keyword detected: CREATE\"]")
    List<String> errors,
    @Schema(description = "Maximum allowed rows for query execution.", example = "200")
    int maxRows,
    @Schema(description = "Execution timeout in seconds.", example = "15")
    int timeoutSeconds,
    @Schema(description = "Immutable runtime policy snapshot applied to this request.")
    QueryPolicyResponse policy
) {

    public QueryValidationResponse(
        boolean valid,
        String cypher,
        Map<String, Object> parameters,
        List<String> errors,
        int maxRows,
        int timeoutSeconds
    ) {
        this(valid, cypher, parameters, errors, maxRows, timeoutSeconds,
            new QueryPolicyResponse(maxRows, timeoutSeconds, true, List.of()));
    }
}
