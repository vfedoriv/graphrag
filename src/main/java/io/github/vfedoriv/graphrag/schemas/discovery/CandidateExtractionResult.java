package io.github.vfedoriv.graphrag.schemas.discovery;

import java.util.List;

/** Top-level object is intentional: several provider structured-output APIs reject top-level arrays. */
public record CandidateExtractionResult(
    List<NodeCandidate> nodes,
    List<NodePropertyCandidate> nodeProperties,
    List<NodeKeyCandidate> nodeKeys,
    List<RelationshipCandidate> relationships,
    List<RelationshipPropertyCandidate> relationshipProperties,
    List<AliasSuggestion> aliasSuggestions
) {
    public CandidateExtractionResult {
        nodes = safe(nodes);
        nodeProperties = safe(nodeProperties);
        nodeKeys = safe(nodeKeys);
        relationships = safe(relationships);
        relationshipProperties = safe(relationshipProperties);
        aliasSuggestions = safe(aliasSuggestions);
    }

    public record NodeCandidate(String label, String description, Double confidence, String origin) {
    }

    public record NodePropertyCandidate(
        String nodeLabel, String name, String type, Boolean required, Double confidence, String origin
    ) {
    }

    public record NodeKeyCandidate(String nodeLabel, List<String> properties, Double confidence, String origin) {
        public NodeKeyCandidate {
            properties = safe(properties);
        }
    }

    public record RelationshipCandidate(
        String type, String fromLabel, String toLabel, String description, Double confidence, String origin
    ) {
    }

    public record RelationshipPropertyCandidate(
        String relationshipType, String fromLabel, String toLabel, String name, String type,
        Boolean required, Double confidence, String origin
    ) {
    }

    public record AliasSuggestion(String first, String second, String rationale, Double confidence) {
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
