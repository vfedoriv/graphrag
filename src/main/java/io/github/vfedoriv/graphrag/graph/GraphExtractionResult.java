package io.github.vfedoriv.graphrag.graph;

import java.util.List;
import java.util.Map;

public record GraphExtractionResult(
    List<ExtractedNode> nodes,
    List<ExtractedRelationship> relationships
) {
    public GraphExtractionResult {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
    }

    public record ExtractedNode(
        String label,
        Map<String, Object> properties,
        Double confidence
    ) {
    }

    public record ExtractedRelationship(
        String type,
        String fromLabel,
        Map<String, Object> fromKey,
        String toLabel,
        Map<String, Object> toKey,
        Map<String, Object> properties,
        Double confidence
    ) {
    }
}
