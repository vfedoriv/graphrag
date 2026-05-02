package io.github.vfedoriv.graphrag.schema;

import java.util.List;

public record SchemaDocument(
    String name,
    Integer version,
    String description,
    List<NodeDefinition> nodes,
    List<RelationshipDefinition> relationships,
    List<IndexDefinition> indexes,
    List<VectorIndexDefinition> vectorIndexes
) {
    public record NodeDefinition(
        String label,
        String description,
        String key,
        List<PropertyDefinition> properties
    ) {
    }

    public record RelationshipDefinition(
        String type,
        String from,
        String to,
        String description,
        List<PropertyDefinition> properties
    ) {
    }

    public record PropertyDefinition(
        String name,
        String type,
        Boolean required
    ) {
    }

    public record IndexDefinition(
        String label,
        List<String> properties,
        Boolean unique
    ) {
    }

    public record VectorIndexDefinition(
        String name,
        String label,
        String property,
        Integer dimensions,
        String similarity
    ) {
    }
}
