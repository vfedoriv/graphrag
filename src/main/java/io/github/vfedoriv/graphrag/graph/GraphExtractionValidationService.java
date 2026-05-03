package io.github.vfedoriv.graphrag.graph;

import io.github.vfedoriv.graphrag.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GraphExtractionValidationService {

    private static final Logger log = LoggerFactory.getLogger(GraphExtractionValidationService.class);

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GraphExtractionValidationService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public GraphExtractionResult validate(GraphExtractionResult result, SchemaDocument schema) {
        if (result == null) {
            throw new IllegalArgumentException("Extraction payload must not be null");
        }
        logPayload(result);
        Map<String, SchemaDocument.NodeDefinition> nodeDefs = new HashMap<>();
        for (var node : schema.nodes()) {
            nodeDefs.put(node.label(), node);
        }
        Set<String> relDefs = new HashSet<>();
        for (var rel : schema.relationships()) {
            relDefs.add(rel.type() + "|" + rel.from() + "|" + rel.to());
        }

        if (result.nodes() != null && result.nodes().size() > appProperties.extraction().maxEntitiesPerChunk()) {
            throw new IllegalArgumentException("Too many extracted entities for chunk");
        }
        if (result.relationships() != null
            && result.relationships().size() > appProperties.extraction().maxRelationshipsPerChunk()) {
            throw new IllegalArgumentException("Too many extracted relationships for chunk");
        }

        List<GraphExtractionResult.ExtractedNode> normalizedNodes = normalizeNodes(result.nodes(), nodeDefs);
        List<GraphExtractionResult.ExtractedRelationship> normalizedRelationships =
            normalizeRelationships(result.relationships(), nodeDefs, normalizedNodes);
        GraphExtractionResult normalized = new GraphExtractionResult(normalizedNodes, normalizedRelationships);

        if (normalized.nodes() != null) {
            for (var extracted : normalized.nodes()) {
                SchemaDocument.NodeDefinition nodeDef = nodeDefs.get(extracted.label());
                if (nodeDef == null) {
                    throw new IllegalArgumentException("Unknown node label: " + extracted.label());
                }
                Object keyValue = extracted.properties() == null ? null : extracted.properties().get(nodeDef.key());
                if (keyValue == null || keyValue.toString().isBlank()) {
                    throw new IllegalArgumentException("Node key is missing: " + extracted.label() + "." + nodeDef.key());
                }
            }
        }

        if (normalized.relationships() != null) {
            for (var extracted : normalized.relationships()) {
                String ruleKey = extracted.type() + "|" + extracted.fromLabel() + "|" + extracted.toLabel();
                if (!relDefs.contains(ruleKey)) {
                    throw new IllegalArgumentException("Unknown relationship rule: " + ruleKey);
                }
                if (extracted.fromKey() == null || extracted.fromKey().isEmpty()
                    || extracted.toKey() == null || extracted.toKey().isEmpty()) {
                    throw new IllegalArgumentException("Relationship endpoint keys must not be empty");
                }
            }
        }
        return normalized;
    }

    private List<GraphExtractionResult.ExtractedNode> normalizeNodes(
        List<GraphExtractionResult.ExtractedNode> nodes,
        Map<String, SchemaDocument.NodeDefinition> nodeDefs
    ) {
        if (nodes == null) {
            return null;
        }
        List<GraphExtractionResult.ExtractedNode> normalized = new ArrayList<>(nodes.size());
        for (var node : nodes) {
            Map<String, Object> props = new HashMap<>(node.properties() == null ? Map.of() : node.properties());
            SchemaDocument.NodeDefinition def = nodeDefs.get(node.label());
            if (def != null) {
                Object keyValue = props.get(def.key());
                if (keyValue == null || keyValue.toString().isBlank()) {
                    String generated = generateNodeKey(node.label(), def.key(), props);
                    props.put(def.key(), generated);
                    log.warn("Generated missing node key: {}.{}={}", node.label(), def.key(), generated);
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
        if (relationships == null) {
            return null;
        }
        List<GraphExtractionResult.ExtractedRelationship> normalized = new ArrayList<>(relationships.size());
        for (var rel : relationships) {
            Map<String, Object> fromKey = new HashMap<>(rel.fromKey() == null ? Map.of() : rel.fromKey());
            Map<String, Object> toKey = new HashMap<>(rel.toKey() == null ? Map.of() : rel.toKey());

            SchemaDocument.NodeDefinition fromDef = nodeDefs.get(rel.fromLabel());
            SchemaDocument.NodeDefinition toDef = nodeDefs.get(rel.toLabel());
            if (fromDef != null) {
                fillEndpointKey(rel.fromLabel(), fromDef.key(), fromKey, normalizedNodes);
            }
            if (toDef != null) {
                fillEndpointKey(rel.toLabel(), toDef.key(), toKey, normalizedNodes);
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
        String keyName,
        Map<String, Object> endpointKey,
        List<GraphExtractionResult.ExtractedNode> normalizedNodes
    ) {
        Object current = endpointKey.get(keyName);
        if (current != null && !current.toString().isBlank()) {
            return;
        }
        if (normalizedNodes == null) {
            return;
        }
        List<GraphExtractionResult.ExtractedNode> sameLabel = normalizedNodes.stream()
            .filter(node -> Objects.equals(node.label(), label))
            .toList();
        if (sameLabel.size() == 1) {
            Object inferred = sameLabel.getFirst().properties() == null ? null : sameLabel.getFirst().properties().get(keyName);
            if (inferred != null && !inferred.toString().isBlank()) {
                endpointKey.put(keyName, inferred);
                log.warn("Filled missing relationship endpoint key from node payload: {}.{}={}", label, keyName, inferred);
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
            log.info("Graph extraction payload before validation: {}", objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            log.warn("Failed to serialize graph extraction payload for logging: {}", ex.getMessage());
        }
    }
}
