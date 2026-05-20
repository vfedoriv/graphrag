package io.github.vfedoriv.graphrag.graph;

import io.github.vfedoriv.graphrag.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.error.GraphExtractionValidationException;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.schema.NodeKeySupport;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        if (result == null) {
            throw new GraphExtractionValidationException("Extraction payload must not be null");
        }
        log.info(
            "Validating graph extraction payload: schemaName={}, nodes={}, relationships={}",
            schema.name(),
            result.nodes().size(),
            result.relationships().size()
        );
        logPayload(result);
        Map<String, SchemaDocument.NodeDefinition> nodeDefs = new HashMap<>();
        for (SchemaDocument.NodeDefinition node : schema.nodes()) {
            nodeDefs.put(node.label(), node);
        }
        Set<String> relDefs = new HashSet<>();
        for (SchemaDocument.RelationshipDefinition rel : schema.relationships()) {
            relDefs.add(rel.type() + "|" + rel.from() + "|" + rel.to());
        }

        if (result.nodes().size() > appProperties.extraction().maxEntitiesPerChunk()) {
            throw new GraphExtractionValidationException("Too many extracted entities for chunk");
        }
        if (result.relationships().size() > appProperties.extraction().maxRelationshipsPerChunk()) {
            throw new GraphExtractionValidationException("Too many extracted relationships for chunk");
        }

        List<GraphExtractionResult.ExtractedNode> normalizedNodes = normalizeNodes(result.nodes(), nodeDefs);
        List<GraphExtractionResult.ExtractedRelationship> normalizedRelationships =
            normalizeRelationships(result.relationships(), nodeDefs, normalizedNodes);
        GraphExtractionResult normalized = new GraphExtractionResult(normalizedNodes, normalizedRelationships);

        for (GraphExtractionResult.ExtractedNode extracted : normalized.nodes()) {
            SchemaDocument.NodeDefinition nodeDef = nodeDefs.get(extracted.label());
            if (nodeDef == null) {
                throw new GraphExtractionValidationException("Unknown node label: " + extracted.label());
            }
            List<String> keyNames = NodeKeySupport.normalizedKeys(nodeDef);
            for (String keyName : keyNames) {
                Object keyValue = extracted.properties() == null ? null : extracted.properties().get(keyName);
                if (keyValue == null || keyValue.toString().isBlank()) {
                    throw new GraphExtractionValidationException("Node key is missing: " + extracted.label() + "." + keyName);
                }
            }
        }

        List<GraphExtractionResult.ExtractedRelationship> filteredRelationships =
            new ArrayList<>(normalized.relationships().size());
        for (GraphExtractionResult.ExtractedRelationship extracted : normalized.relationships()) {
            String ruleKey = extracted.type() + "|" + extracted.fromLabel() + "|" + extracted.toLabel();
            if (!relDefs.contains(ruleKey)) {
                log.warn(
                    "Dropped schema-invalid relationship triple: schemaName={}, triple={}",
                    schema.name(),
                    ruleKey
                );
                continue;
            }
            if (extracted.fromKey() == null || extracted.fromKey().isEmpty()
                || extracted.toKey() == null || extracted.toKey().isEmpty()) {
                throw new GraphExtractionValidationException("Relationship endpoint keys must not be empty");
            }
            validateRelationshipEndpointKeyComponents(extracted, nodeDefs);
            filteredRelationships.add(extracted);
        }
        normalized = new GraphExtractionResult(normalized.nodes(), filteredRelationships);
        log.info(
            "Graph extraction payload validated: schemaName={}, nodes={}, relationships={}",
            schema.name(),
            normalized.nodes().size(),
            normalized.relationships().size()
        );
        return normalized;
    }

    private List<GraphExtractionResult.ExtractedNode> normalizeNodes(
        List<GraphExtractionResult.ExtractedNode> nodes,
        Map<String, SchemaDocument.NodeDefinition> nodeDefs
    ) {
        List<GraphExtractionResult.ExtractedNode> normalized = new ArrayList<>(nodes.size());
        for (GraphExtractionResult.ExtractedNode node : nodes) {
            Map<String, Object> props = new HashMap<>(node.properties() == null ? Map.of() : node.properties());
            SchemaDocument.NodeDefinition def = nodeDefs.get(node.label());
            if (def != null) {
                List<String> keyNames = NodeKeySupport.normalizedKeys(def);
                for (String keyName : keyNames) {
                    Object keyValue = props.get(keyName);
                    if (keyValue == null || keyValue.toString().isBlank()) {
                        String generated = generateNodeKey(node.label(), keyName, props);
                        props.put(keyName, generated);
                        log.warn("Generated missing node key: {}.{}={}", node.label(), keyName, generated);
                    }
                }
            }
            normalized.add(new GraphExtractionResult.ExtractedNode(node.label(), props, node.confidence()));
        }
        return normalized;
    }

    private List<GraphExtractionResult.ExtractedRelationship> normalizeRelationships(
        List<GraphExtractionResult.ExtractedRelationship> relationships,
        Map<String, SchemaDocument.NodeDefinition> nodeDefs,
        List<GraphExtractionResult.ExtractedNode> normalizedNodes
    ) {
        List<GraphExtractionResult.ExtractedRelationship> normalized = new ArrayList<>(relationships.size());
        for (GraphExtractionResult.ExtractedRelationship rel : relationships) {
            Map<String, Object> fromKey = new HashMap<>(rel.fromKey() == null ? Map.of() : rel.fromKey());
            Map<String, Object> toKey = new HashMap<>(rel.toKey() == null ? Map.of() : rel.toKey());

            SchemaDocument.NodeDefinition fromDef = nodeDefs.get(rel.fromLabel());
            SchemaDocument.NodeDefinition toDef = nodeDefs.get(rel.toLabel());
            if (fromDef != null) {
                fillEndpointKey(rel.fromLabel(), NodeKeySupport.normalizedKeys(fromDef), fromKey, normalizedNodes);
            }
            if (toDef != null) {
                fillEndpointKey(rel.toLabel(), NodeKeySupport.normalizedKeys(toDef), toKey, normalizedNodes);
            }

            normalized.add(new GraphExtractionResult.ExtractedRelationship(
                rel.type(),
                rel.fromLabel(),
                fromKey,
                rel.toLabel(),
                toKey,
                rel.properties(),
                rel.confidence()
            ));
        }
        return normalized;
    }

    private void fillEndpointKey(
        String label,
        List<String> keyNames,
        Map<String, Object> endpointKey,
        List<GraphExtractionResult.ExtractedNode> normalizedNodes
    ) {
        if (normalizedNodes == null) {
            return;
        }
        List<GraphExtractionResult.ExtractedNode> sameLabel = normalizedNodes.stream()
            .filter(node -> Objects.equals(node.label(), label))
            .toList();
        if (sameLabel.size() == 1) {
            for (String keyName : keyNames) {
                Object current = endpointKey.get(keyName);
                if (current != null && !current.toString().isBlank()) {
                    continue;
                }
                Object inferred = sameLabel.getFirst().properties() == null ? null : sameLabel.getFirst().properties().get(keyName);
                if (inferred != null && !inferred.toString().isBlank()) {
                    endpointKey.put(keyName, inferred);
                    log.warn("Filled missing relationship endpoint key from node payload: {}.{}={}", label, keyName, inferred);
                }
            }
        }
    }

    private void validateRelationshipEndpointKeyComponents(
        GraphExtractionResult.ExtractedRelationship relationship,
        Map<String, SchemaDocument.NodeDefinition> nodeDefs
    ) {
        SchemaDocument.NodeDefinition fromDef = nodeDefs.get(relationship.fromLabel());
        SchemaDocument.NodeDefinition toDef = nodeDefs.get(relationship.toLabel());
        validateRequiredKeyComponents("from", relationship.fromLabel(), relationship.fromKey(), fromDef);
        validateRequiredKeyComponents("to", relationship.toLabel(), relationship.toKey(), toDef);
    }

    private void validateRequiredKeyComponents(
        String endpoint,
        String label,
        Map<String, Object> endpointKey,
        SchemaDocument.NodeDefinition nodeDefinition
    ) {
        List<String> keyNames = NodeKeySupport.normalizedKeys(nodeDefinition);
        for (String keyName : keyNames) {
            Object value = endpointKey.get(keyName);
            if (value == null || value.toString().isBlank()) {
                throw new GraphExtractionValidationException(
                    "Relationship %s endpoint key is missing component: %s.%s".formatted(endpoint, label, keyName)
                );
            }
        }
    }

    private String generateNodeKey(String label, String keyName, Map<String, Object> props) {
        Object preferred = firstNonBlank(
            props.get("id"),
            props.get("name"),
            props.get("code"),
            props.get("number"),
            props.get("title")
        );
        if (preferred != null) {
            return preferred.toString();
        }
        int hash = Objects.hash(label, keyName, props);
        return label + "-" + keyName + "-" + Integer.toUnsignedString(hash);
    }

    private Object firstNonBlank(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate != null && !candidate.toString().isBlank()) {
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
}
