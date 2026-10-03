package io.github.vfedoriv.graphrag.schemas.contracts;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaFormat;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSourceType;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaStatus;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.time.Instant;
import java.util.List;

/** Immutable stored schema value; metadata lookups may omit the parsed schema. */
public record SchemaSnapshot(
    String knowledgeBaseId,
    String schemaDefinitionId,
    String name,
    int version,
    SchemaSourceType sourceType,
    SchemaFormat format,
    SchemaStatus status,
    String content,
    String contentHash,
    Instant createdAt,
    Instant updatedAt,
    SchemaDocument schema
) {
    public SchemaSnapshot {
        schema = copy(schema);
    }

    private static SchemaDocument copy(SchemaDocument source) {
        if (source == null) {
            return null;
        }
        return new SchemaDocument(
            source.name(), source.version(), source.description(),
            copyNodes(source.nodes()), copyRelationships(source.relationships()),
            copyIndexes(source.indexes()), source.vectorIndexes() == null ? null : List.copyOf(source.vectorIndexes())
        );
    }

    private static List<SchemaDocument.NodeDefinition> copyNodes(List<SchemaDocument.NodeDefinition> nodes) {
        if (nodes == null) {
            return null;
        }
        return nodes.stream().map(node -> new SchemaDocument.NodeDefinition(
            node.label(), node.description(),
            node.key() == null ? null : List.copyOf(node.key()),
            node.properties() == null ? null : List.copyOf(node.properties())
        )).toList();
    }

    private static List<SchemaDocument.RelationshipDefinition> copyRelationships(
        List<SchemaDocument.RelationshipDefinition> relationships
    ) {
        if (relationships == null) {
            return null;
        }
        return relationships.stream().map(relationship -> new SchemaDocument.RelationshipDefinition(
            relationship.type(), relationship.from(), relationship.to(), relationship.description(),
            relationship.properties() == null ? null : List.copyOf(relationship.properties())
        )).toList();
    }

    private static List<SchemaDocument.IndexDefinition> copyIndexes(List<SchemaDocument.IndexDefinition> indexes) {
        if (indexes == null) {
            return null;
        }
        return indexes.stream().map(index -> new SchemaDocument.IndexDefinition(
            index.label(), index.properties() == null ? null : List.copyOf(index.properties()), index.unique()
        )).toList();
    }
}
