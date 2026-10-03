package io.github.vfedoriv.graphrag.search.query.domain;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.HashSet;
import java.util.Set;

public final class CypherSchemaSupport {

    private CypherSchemaSupport() {
    }

    public static Set<String> allowedLabels(SchemaDocument schema, Set<String> infraLabels) {
        Set<String> allowedLabels = new HashSet<>(infraLabels);
        for (SchemaDocument.NodeDefinition node : schema.nodes()) {
            allowedLabels.add(node.label());
        }
        return allowedLabels;
    }

    public static Set<String> allowedRelationshipTypes(SchemaDocument schema) {
        Set<String> allowedRelationshipTypes = new HashSet<>();
        for (SchemaDocument.RelationshipDefinition relationship : schema.relationships()) {
            allowedRelationshipTypes.add(relationship.type());
        }
        return allowedRelationshipTypes;
    }

    public static Set<String> allowedProperties(SchemaDocument schema) {
        Set<String> allowedProperties = new HashSet<>(Set.of(
            "id", "sourceDocumentId", "sourceChunkId", "sourceChunkIds", "sourceChunkKind", "sourceChunkText",
            "sourceStart", "sourceEnd", "pageStart", "pageEnd", "structuralPath", "processingRunId",
            "chunkStrategyRevision", "effectiveChunkerRevision", "sourceHash",
            "schemaId", "extractionRunId", "confidence", "createdAt"
        ));
        for (SchemaDocument.NodeDefinition node : schema.nodes()) {
            for (SchemaDocument.PropertyDefinition property : node.properties()) {
                allowedProperties.add(property.name());
            }
        }
        for (SchemaDocument.RelationshipDefinition relationship : schema.relationships()) {
            for (SchemaDocument.PropertyDefinition property : relationship.properties()) {
                allowedProperties.add(property.name());
            }
        }
        return allowedProperties;
    }
}
