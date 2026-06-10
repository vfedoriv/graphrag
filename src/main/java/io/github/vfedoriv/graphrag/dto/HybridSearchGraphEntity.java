package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public record HybridSearchGraphEntity(
    @Schema(description = "Neo4j element id.")
    String elementId,
    @Schema(description = "Node labels.")
    List<String> labels,
    @Schema(description = "Node properties.")
    Map<String, Object> properties
) {
}
