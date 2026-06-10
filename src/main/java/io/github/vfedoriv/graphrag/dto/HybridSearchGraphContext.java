package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HybridSearchGraphContext(
    @Schema(description = "Entities mentioned by or near the matched chunk.")
    List<HybridSearchGraphEntity> entities,
    @Schema(description = "Relationships near the mentioned entities.")
    List<HybridSearchGraphRelationship> relationships
) {
}
