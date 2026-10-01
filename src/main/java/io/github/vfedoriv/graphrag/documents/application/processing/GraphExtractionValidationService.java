package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult;
import io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;
import io.github.vfedoriv.graphrag.error.GraphExtractionValidationException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.schema.NodeKeySupport;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GraphExtractionValidationService {

    private final RuntimeSettingsService runtimeSettingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GraphExtractionValidationService(RuntimeSettingsService runtimeSettingsService) {
        this.runtimeSettingsService = runtimeSettingsService;
    }

    public GraphExtractionResult validate(GraphExtractionResult result, SchemaDocument schema) {
        validateProcessInputs(result, schema);
        logValidationStart(result, schema);
        logPayload(result);

        GraphExtractionSupport.SchemaIndex schemaIndex = GraphExtractionSupport.buildSchemaIndex(schema);
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

    private void enforcePayloadLimits(GraphExtractionResult result) {
        RuntimeSettingsService.ExtractionSettings settings = runtimeSettingsService.extraction();
        if (result.nodes().size() > settings.maxEntitiesPerChunk()) {
            throw new GraphExtractionValidationException("Too many extracted entities for chunk");
        }
        if (result.relationships().size() > settings.maxRelationshipsPerChunk()) {
            throw new GraphExtractionValidationException("Too many extracted relationships for chunk");
        }
    }

    private List<GraphExtractionResult.ExtractedNode> filterNodes(
        List<GraphExtractionResult.ExtractedNode> nodes,
        String schemaName,
        GraphExtractionSupport.SchemaIndex schemaIndex
    ) {
        List<GraphExtractionResult.ExtractedNode> kept = new java.util.ArrayList<>(nodes.size());
        for (GraphExtractionResult.ExtractedNode node : nodes) {
            SchemaDocument.NodeDefinition definition = schemaIndex.nodeDefs().get(node.label());
            if (definition == null) {
                logDroppedNode(schemaName, node, "unknown_label");
                continue;
            }
            GraphExtractionResult.ExtractedNode normalized = GraphExtractionSupport.normalizeNode(node, definition);
            for (String keyName : NodeKeySupport.normalizedKeys(definition)) {
                Object before = node.properties() == null ? null : node.properties().get(keyName);
                Object after = normalized.properties() == null ? null : normalized.properties().get(keyName);
                if (GraphExtractionSupport.isBlankValue(before) && !GraphExtractionSupport.isBlankValue(after)) {
                    logNodeRepair(schemaName, node.label(), keyName, after);
                }
            }
            if (GraphExtractionSupport.hasCompleteRequiredKeys(normalized.properties(), NodeKeySupport.normalizedKeys(definition))) {
                kept.add(normalized);
            } else {
                logDroppedNode(schemaName, normalized, "incomplete_node_key");
            }
        }
        return kept;
    }

    private List<GraphExtractionResult.ExtractedRelationship> filterRelationships(
        List<GraphExtractionResult.ExtractedRelationship> relationships,
        String schemaName,
        GraphExtractionSupport.SchemaIndex schemaIndex,
        List<GraphExtractionResult.ExtractedNode> keptNodes
    ) {
        List<GraphExtractionResult.ExtractedRelationship> kept = new java.util.ArrayList<>(relationships.size());
        for (GraphExtractionResult.ExtractedRelationship relationship : relationships) {
            GraphExtractionResult.ExtractedRelationship normalized = GraphExtractionSupport.normalizeRelationship(relationship, schemaIndex, keptNodes);
            logEndpointRepairs(schemaName, relationship, normalized);
            String dropReason = GraphExtractionSupport.relationshipDropReason(normalized, schemaIndex, keptNodes);
            if (dropReason == null) {
                kept.add(normalized);
            } else {
                logDroppedRelationship(schemaName, normalized, dropReason);
            }
        }
        return kept;
    }

    private void logDroppedNode(String schemaName, GraphExtractionResult.ExtractedNode node, String reason) {
        log.warn(
            "Dropped extracted node: schemaName={}, reason={}, propertyCount={}, propertyNames={}",
            schemaName,
            reason,
            node.properties() == null ? 0 : node.properties().size(),
            GraphExtractionSupport.sanitizedPropertyNames(node.properties())
        );
    }

    private void logDroppedRelationship(String schemaName, GraphExtractionResult.ExtractedRelationship relationship, String reason) {
        log.warn(
            "Dropped extracted relationship: schemaName={}, reason={}, fromKeyCount={}, toKeyCount={}",
            schemaName,
            reason,
            relationship.fromKey() == null ? 0 : relationship.fromKey().size(),
            relationship.toKey() == null ? 0 : relationship.toKey().size()
        );
    }

    private void logEndpointRepairs(
        String schemaName,
        GraphExtractionResult.ExtractedRelationship original,
        GraphExtractionResult.ExtractedRelationship normalized
    ) {
        logEndpointRepairDiffs(schemaName, original.type(), "from", original.fromLabel(), original.fromKey(), normalized.fromKey());
        logEndpointRepairDiffs(schemaName, original.type(), "to", original.toLabel(), original.toKey(), normalized.toKey());
    }

    private void logEndpointRepairDiffs(
        String schemaName,
        String relationshipType,
        String endpoint,
        String label,
        Map<String, Object> originalKey,
        Map<String, Object> normalizedKey
    ) {
        if (normalizedKey == null || normalizedKey.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : normalizedKey.entrySet()) {
            Object before = originalKey == null ? null : originalKey.get(entry.getKey());
            if (GraphExtractionSupport.isBlankValue(before) && !GraphExtractionSupport.isBlankValue(entry.getValue())) {
                logEndpointRepair(schemaName, relationshipType, endpoint, label, entry.getKey(), entry.getValue());
            }
        }
    }

    private void logNodeRepair(String schemaName, String label, String keyName, Object repairedValue) {
        log.warn(
            "Repaired extracted node key: schemaName={}, reason=node_key_repaired, keyNamePresent={}, valueLength={}, valueFingerprint={}",
            schemaName,
            keyName != null && !keyName.isBlank(),
            LogMetadata.length(String.valueOf(repairedValue)),
            LogMetadata.fingerprint(String.valueOf(repairedValue))
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
            "Repaired relationship endpoint key: schemaName={}, reason=endpoint_key_repaired, endpoint={}, keyNamePresent={}, valueLength={}, valueFingerprint={}",
            schemaName,
            endpoint,
            keyName != null && !keyName.isBlank(),
            LogMetadata.length(String.valueOf(repairedValue)),
            LogMetadata.fingerprint(String.valueOf(repairedValue))
        );
    }

    private void logPayload(GraphExtractionResult result) {
        try {
            String payload = objectMapper.writeValueAsString(result);
            log.info(
                "Graph extraction payload before validation: length={}, fingerprint={}",
                payload.length(),
                LogMetadata.fingerprint(payload)
            );
        } catch (Exception ex) {
            log.error("Failed to serialize graph extraction payload for logging: exceptionType={}", LogMetadata.exceptionType(ex));
        }
    }
}
