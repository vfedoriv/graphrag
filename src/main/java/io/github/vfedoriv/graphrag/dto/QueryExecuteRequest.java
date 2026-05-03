package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record QueryExecuteRequest(
    @Schema(description = "Cypher statement to execute (must pass validation rules).", example = "MATCH (c:Contract) RETURN c.title LIMIT 10")
    @NotBlank String cypher,
    @Schema(description = "Named parameters used by the Cypher statement.", example = "{\"party\":\"Acme Corp\"}")
    Map<String, Object> parameters
) {
}
