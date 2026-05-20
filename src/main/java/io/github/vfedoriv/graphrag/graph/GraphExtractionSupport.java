package io.github.vfedoriv.graphrag.graph;

import io.github.vfedoriv.graphrag.schema.NodeKeySupport;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class GraphExtractionSupport {

    private GraphExtractionSupport() {
    }

    static SchemaIndex buildSchemaIndex(SchemaDocument schema) {
        Map<String, SchemaDocument.NodeDefinition> nodeDefs = new HashMap<>();
        for (SchemaDocument.NodeDefinition node : schema.nodes()) {
            nodeDefs.put(node.label(), node);
        }
        Set<String> relationshipTriples = new HashSet<>();
        for (SchemaDocument.RelationshipDefinition rel : schema.relationships()) {
            relationshipTriples.add(relationshipTriple(rel.type(), rel.from(), rel.to()));
        }
        return new SchemaIndex(nodeDefs, relationshipTriples);
    }

    static GraphExtractionResult.ExtractedNode normalizeNode(
        GraphExtractionResult.ExtractedNode node,
        SchemaDocument.NodeDefinition definition
    ) {
        Map<String, Object> props = new LinkedHashMap<>(node.properties() == null ? Map.of() : node.properties());
        for (String keyName : NodeKeySupport.normalizedKeys(definition)) {
            Object keyValue = props.get(keyName);
            if (isBlankValue(keyValue)) {
                Object repaired = repairNodeKey(props);
                if (!isBlankValue(repaired)) {
                    props.put(keyName, repaired.toString());
                }
            }
        }
        return new GraphExtractionResult.ExtractedNode(node.label(), props, node.confidence());
    }

    static GraphExtractionResult.ExtractedRelationship normalizeRelationship(
        GraphExtractionResult.ExtractedRelationship relationship,
        SchemaIndex schemaIndex,
        List<GraphExtractionResult.ExtractedNode> keptNodes
    ) {
        Map<String, Object> fromKey = new LinkedHashMap<>(relationship.fromKey() == null ? Map.of() : relationship.fromKey());
        Map<String, Object> toKey = new LinkedHashMap<>(relationship.toKey() == null ? Map.of() : relationship.toKey());

        SchemaDocument.NodeDefinition fromDef = schemaIndex.nodeDefs().get(relationship.fromLabel());
        SchemaDocument.NodeDefinition toDef = schemaIndex.nodeDefs().get(relationship.toLabel());
        if (fromDef != null) {
            fillEndpointKey(relationship.fromLabel(), NodeKeySupport.normalizedKeys(fromDef), fromKey, keptNodes);
        }
        if (toDef != null) {
            fillEndpointKey(relationship.toLabel(), NodeKeySupport.normalizedKeys(toDef), toKey, keptNodes);
        }

        return new GraphExtractionResult.ExtractedRelationship(
            relationship.type(),
            relationship.fromLabel(),
            fromKey,
            relationship.toLabel(),
            toKey,
            relationship.properties(),
            relationship.confidence()
        );
    }

    static String relationshipDropReason(
        GraphExtractionResult.ExtractedRelationship relationship,
        SchemaIndex schemaIndex,
        List<GraphExtractionResult.ExtractedNode> keptNodes
    ) {
        SchemaDocument.NodeDefinition fromDef = schemaIndex.nodeDefs().get(relationship.fromLabel());
        SchemaDocument.NodeDefinition toDef = schemaIndex.nodeDefs().get(relationship.toLabel());
        if (fromDef == null || toDef == null) {
            return "unknown_endpoint_label";
        }
        if (!schemaIndex.relationshipTriples().contains(relationshipTriple(relationship.type(), relationship.fromLabel(), relationship.toLabel()))) {
            return "invalid_relationship_triple";
        }
        if (relationship.fromKey() == null || relationship.fromKey().isEmpty() || relationship.toKey() == null || relationship.toKey().isEmpty()) {
            return "empty_endpoint_key";
        }
        if (!hasCompleteRequiredKeys(relationship.fromKey(), NodeKeySupport.normalizedKeys(fromDef))) {
            return "incomplete_from_endpoint_key";
        }
        if (!hasCompleteRequiredKeys(relationship.toKey(), NodeKeySupport.normalizedKeys(toDef))) {
            return "incomplete_to_endpoint_key";
        }
        if (!endpointMatchesKeptNode(relationship.fromLabel(), relationship.fromKey(), NodeKeySupport.normalizedKeys(fromDef), keptNodes)) {
            return "from_endpoint_not_kept";
        }
        if (!endpointMatchesKeptNode(relationship.toLabel(), relationship.toKey(), NodeKeySupport.normalizedKeys(toDef), keptNodes)) {
            return "to_endpoint_not_kept";
        }
        return null;
    }

    static boolean hasCompleteRequiredKeys(Map<String, Object> values, List<String> keyNames) {
        if (keyNames.isEmpty()) {
            return true;
        }
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String keyName : keyNames) {
            if (isBlankValue(values.get(keyName))) {
                return false;
            }
        }
        return true;
    }

    static Object repairNodeKey(Map<String, Object> props) {
        return firstNonBlank(
            props.get("id"),
            props.get("name"),
            props.get("code"),
            props.get("number"),
            props.get("title")
        );
    }

    static List<String> sanitizedPropertyNames(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(properties.keySet());
    }

    static String relationshipTriple(String type, String from, String to) {
        return type + "|" + from + "|" + to;
    }

    static boolean isBlankValue(Object value) {
        return value == null || value.toString().isBlank();
    }

    static Object firstNonBlank(Object... candidates) {
        for (Object candidate : candidates) {
            if (!isBlankValue(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static void fillEndpointKey(
        String label,
        List<String> keyNames,
        Map<String, Object> endpointKey,
        List<GraphExtractionResult.ExtractedNode> keptNodes
    ) {
        List<GraphExtractionResult.ExtractedNode> sameLabel = keptNodes.stream()
            .filter(node -> Objects.equals(node.label(), label))
            .toList();
        if (sameLabel.size() != 1) {
            return;
        }
        GraphExtractionResult.ExtractedNode sourceNode = sameLabel.getFirst();
        for (String keyName : keyNames) {
            Object current = endpointKey.get(keyName);
            if (!isBlankValue(current)) {
                continue;
            }
            Object inferred = sourceNode.properties() == null ? null : sourceNode.properties().get(keyName);
            if (!isBlankValue(inferred)) {
                endpointKey.put(keyName, inferred);
            }
        }
    }

    private static boolean endpointMatchesKeptNode(
        String label,
        Map<String, Object> endpointKey,
        List<String> keyNames,
        List<GraphExtractionResult.ExtractedNode> keptNodes
    ) {
        for (GraphExtractionResult.ExtractedNode node : keptNodes) {
            if (!Objects.equals(label, node.label())) {
                continue;
            }
            if (keysMatch(endpointKey, node.properties(), keyNames)) {
                return true;
            }
        }
        return false;
    }

    private static boolean keysMatch(Map<String, Object> left, Map<String, Object> right, List<String> keyNames) {
        for (String keyName : keyNames) {
            if (!Objects.equals(String.valueOf(left.get(keyName)), String.valueOf(right.get(keyName)))) {
                return false;
            }
        }
        return true;
    }

    record SchemaIndex(
        Map<String, SchemaDocument.NodeDefinition> nodeDefs,
        Set<String> relationshipTriples
    ) {
    }
}
