package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HybridSearchGraphContext(
    @Schema(description = "Entities mentioned by or near the matched chunk.")
    List<HybridSearchGraphEntity> entities,
    @Schema(description = "Relationships near the mentioned entities.")
    List<HybridSearchGraphRelationship> relationships,
    @Schema(description = "Authoritative parent citations for graph-derived evidence.")
    List<HybridSearchGraphEvidence> evidence
) {
    public HybridSearchGraphContext {
        entities = entities == null ? List.of() : List.copyOf(entities);
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public HybridSearchGraphContext(
        List<HybridSearchGraphEntity> entities,
        List<HybridSearchGraphRelationship> relationships
    ) {
        this(entities, relationships, List.of());
    }
}
