package io.github.vfedoriv.graphrag.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.error.GraphExtractionValidationException;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GraphExtractionValidationService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GraphExtractionValidationService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public GraphExtractionResult validate(GraphExtractionResult result, SchemaDocument schema) {
        validateProcessInputs(result, schema);
        logValidationStart(result, schema);
        logPayload(result);

        SchemaIndex schemaIndex = buildSchemaIndex(schema);
        enforcePayloadLimits(result);

        List<GraphExtractionResult.ExtractedNode> keptNodes = filterNodes(result.nodes(), schema.name(), schemaIndex);
        List<GraphExtractionResult.ExtractedRelationship> keptRelationships = filterRelationships(
            result.relationships(),
            schema.name(),
            schemaIndex,
            keptNodes
        );
        GraphExtractionResult sanitized = new GraphExtractionResult(keptNodes, keptRelationships);

        log.info(
            "Graph extraction payload sanitized: schemaName={}, nodesBefore={}, relationshipsBefore={}, nodesAfter={}, relationshipsAfter={}",
            schema.name(),
            result.nodes().size(),
            result.relationships().size(),
            sanitized.nodes().size(),
            sanitized.relationships().size()
        );
        return sanitized;
    }

    private void validateProcessInputs(GraphExtractionResult result, SchemaDocument schema) {
        if (result == null) {
            throw new GraphExtractionValidationException("Extraction payload must not be null");
        }
        if (schema == null) {
            throw new GraphExtractionValidationException("Extraction schema must not be null");
        }
    }

    private void logValidationStart(GraphExtractionResult result, SchemaDocument schema) {
        log.info(
            "Validating graph extraction payload: schemaName={}, nodes={}, relationships={}",
            schema.name(),
            result.nodes().size(),
            result.relationships().size()
        );
    }

    private SchemaIndex buildSchemaIndex(SchemaDocument schema) {
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

    private void enforcePayloadLimits(GraphExtractionResult result) {
        if (result.nodes().size() > appProperties.extraction().maxEntitiesPerChunk()) {
            throw new GraphExtractionValidationException("Too many extracted entities for chunk");
        }
        if (result.relationships().size() > appProperties.extraction().maxRelationshipsPerChunk()) {
            throw new GraphExtractionValidationException("Too many extracted relationships for chunk");
        }
    }

    private List<GraphExtractionResult.ExtractedNode> filterNodes(
        List<GraphExtractionResult.ExtractedNode> nodes,
        String schemaName,
        SchemaIndex schemaIndex
    ) {
        List<GraphExtractionResult.ExtractedNode> kept = new ArrayList<>(nodes.size());
        for (GraphExtractionResult.ExtractedNode node : nodes) {
            SchemaDocument.NodeDefinition definition = schemaIndex.nodeDefs().get(node.label());
            if (definition == null) {
                logDroppedNode(schemaName, node, "unknown_label");
                continue;
            }
            GraphExtractionResult.ExtractedNode normalized = normalizeNode(node, schemaName, definition);
            if (hasCompleteRequiredKeys(normalized.properties(), NodeKeySupport.normalizedKeys(definition))) {
                kept.add(normalized);
            } else {
                logDroppedNode(schemaName, normalized, "incomplete_node_key");
            }
        }
        return kept;
    }

    private GraphExtractionResult.ExtractedNode normalizeNode(
        GraphExtractionResult.ExtractedNode node,
        String schemaName,
        SchemaDocument.NodeDefinition definition
    ) {
        Map<String, Object> props = new LinkedHashMap<>(node.properties() == null ? Map.of() : node.properties());
        for (String keyName : NodeKeySupport.normalizedKeys(definition)) {
            Object keyValue = props.get(keyName);
            if (isBlankValue(keyValue)) {
                Object repaired = repairNodeKey(props);
                if (!isBlankValue(repaired)) {
                    props.put(keyName, repaired.toString());
                    logNodeRepair(schemaName, node.label(), keyName, repaired);
                }
            }
        }
        return new GraphExtractionResult.ExtractedNode(node.label(), props, node.confidence());
    }

    private List<GraphExtractionResult.ExtractedRelationship> filterRelationships(
        List<GraphExtractionResult.ExtractedRelationship> relationships,
        String schemaName,
        SchemaIndex schemaIndex,
        List<GraphExtractionResult.ExtractedNode> keptNodes
    ) {
        List<GraphExtractionResult.ExtractedRelationship> kept = new ArrayList<>(relationships.size());
        for (GraphExtractionResult.ExtractedRelationship relationship : relationships) {
            GraphExtractionResult.ExtractedRelationship normalized = normalizeRelationship(relationship, schemaName, schemaIndex, keptNodes);
            String dropReason = relationshipDropReason(normalized, schemaIndex, keptNodes);
            if (dropReason == null) {
                kept.add(normalized);
            } else {
                logDroppedRelationship(schemaName, normalized, dropReason);
            }
        }
        return kept;
    }

    private GraphExtractionResult.ExtractedRelationship normalizeRelationship(
        GraphExtractionResult.ExtractedRelationship relationship,
        String schemaName,
        SchemaIndex schemaIndex,
        List<GraphExtractionResult.ExtractedNode> keptNodes
    ) {
        Map<String, Object> fromKey = new LinkedHashMap<>(relationship.fromKey() == null ? Map.of() : relationship.fromKey());
        Map<String, Object> toKey = new LinkedHashMap<>(relationship.toKey() == null ? Map.of() : relationship.toKey());

        SchemaDocument.NodeDefinition fromDef = schemaIndex.nodeDefs().get(relationship.fromLabel());
        SchemaDocument.NodeDefinition toDef = schemaIndex.nodeDefs().get(relationship.toLabel());
        if (fromDef != null) {
            fillEndpointKey(schemaName, relationship.type(), "from", relationship.fromLabel(), NodeKeySupport.normalizedKeys(fromDef), fromKey, keptNodes);
        }
        if (toDef != null) {
            fillEndpointKey(schemaName, relationship.type(), "to", relationship.toLabel(), NodeKeySupport.normalizedKeys(toDef), toKey, keptNodes);
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

    private String relationshipDropReason(
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

    private void fillEndpointKey(
        String schemaName,
        String relationshipType,
        String endpoint,
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
                logEndpointRepair(schemaName, relationshipType, endpoint, label, keyName, inferred);
            }
        }
    }

    private boolean hasCompleteRequiredKeys(Map<String, Object> values, List<String> keyNames) {
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

    private boolean endpointMatchesKeptNode(
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

    private boolean keysMatch(Map<String, Object> left, Map<String, Object> right, List<String> keyNames) {
        for (String keyName : keyNames) {
            if (!Objects.equals(String.valueOf(left.get(keyName)), String.valueOf(right.get(keyName)))) {
                return false;
            }
        }
        return true;
    }

    private Object repairNodeKey(Map<String, Object> props) {
        return firstNonBlank(
            props.get("id"),
            props.get("name"),
            props.get("code"),
            props.get("number"),
            props.get("title")
        );
    }

    private void logDroppedNode(String schemaName, GraphExtractionResult.ExtractedNode node, String reason) {
        log.warn(
            "Dropped extracted node: schemaName={}, reason={}, label={}, propertyNames={}",
            schemaName,
            reason,
            LogSanitizer.preview(node.label()),
            sanitizedPropertyNames(node.properties())
        );
    }

    private void logDroppedRelationship(String schemaName, GraphExtractionResult.ExtractedRelationship relationship, String reason) {
        log.warn(
            "Dropped extracted relationship: schemaName={}, reason={}, triple={}, fromKeyNames={}, toKeyNames={}",
            schemaName,
            reason,
            LogSanitizer.preview(relationshipTriple(relationship.type(), relationship.fromLabel(), relationship.toLabel())),
            sanitizedPropertyNames(relationship.fromKey()),
            sanitizedPropertyNames(relationship.toKey())
        );
    }

    private void logNodeRepair(String schemaName, String label, String keyName, Object repairedValue) {
        log.warn(
            "Repaired extracted node key: schemaName={}, reason=node_key_repaired, label={}, keyName={}, valuePreview={}",
            schemaName,
            LogSanitizer.preview(label),
            LogSanitizer.preview(keyName),
            LogSanitizer.preview(String.valueOf(repairedValue))
        );
    }

    private void logEndpointRepair(
        String schemaName,
        String relationshipType,
        String endpoint,
        String label,
        String keyName,
        Object repairedValue
    ) {
        log.warn(
            "Repaired relationship endpoint key: schemaName={}, reason=endpoint_key_repaired, type={}, endpoint={}, label={}, keyName={}, valuePreview={}",
            schemaName,
            LogSanitizer.preview(relationshipType),
            endpoint,
            LogSanitizer.preview(label),
            LogSanitizer.preview(keyName),
            LogSanitizer.preview(String.valueOf(repairedValue))
        );
    }

    private List<String> sanitizedPropertyNames(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String name : properties.keySet()) {
            names.add(LogSanitizer.preview(name));
        }
        return names;
    }

    private String relationshipTriple(String type, String from, String to) {
        return type + "|" + from + "|" + to;
    }

    private boolean isBlankValue(Object value) {
        return value == null || value.toString().isBlank();
    }

    private Object firstNonBlank(Object... candidates) {
        for (Object candidate : candidates) {
            if (!isBlankValue(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void logPayload(GraphExtractionResult result) {
        try {
            String payload = objectMapper.writeValueAsString(result);
            log.info("Graph extraction payload before validation: length={}, preview={}", payload.length(), LogSanitizer.preview(payload));
            if (log.isDebugEnabled()) {
                log.debug("Graph extraction payload before validation: {}", payload);
            }
        } catch (Exception ex) {
            log.error("Failed to serialize graph extraction payload for logging: {}", ex.getMessage(), ex);
        }
    }

    private record SchemaIndex(
        Map<String, SchemaDocument.NodeDefinition> nodeDefs,
        Set<String> relationshipTriples
    ) {
    }
}
