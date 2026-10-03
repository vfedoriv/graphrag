package io.github.vfedoriv.graphrag.search.query.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record QueryValidateRequest(
    @Schema(description = "Cypher statement to validate.", example = "MATCH (c:Contract) RETURN c.title LIMIT 10")
    @NotBlank String cypher,
    @Schema(description = "Named parameters used by the Cypher statement.", example = "{\"status\":\"ACTIVE\"}")
    Map<String, Object> parameters
) {
}
