package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record HybridSearchGraphRelationship(
    @Schema(description = "Neo4j relationship element id.")
    String elementId,
    @Schema(description = "Relationship type.")
    String type,
    @Schema(description = "Start node element id.")
    String startNodeElementId,
    @Schema(description = "End node element id.")
    String endNodeElementId,
    @Schema(description = "Relationship properties.")
    Map<String, Object> properties
) {
}
