package io.github.vfedoriv.graphrag.graph;

import io.github.vfedoriv.graphrag.schema.NodeKeySupport;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GraphWriteService {

    private final Neo4jClient neo4jClient;

    public GraphWriteService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public void write(
        String extractionRunId,
        String schemaId,
        String documentId,
        String chunkId,
        SchemaDocument schema,
        GraphExtractionResult result
    ) {
        int nodeCount = result.nodes() == null ? 0 : result.nodes().size();
        int relationshipCount = result.relationships() == null ? 0 : result.relationships().size();
        log.info(
            "Writing graph extraction result: runId={}, documentId={}, chunkId={}, nodes={}, relationships={}",
            extractionRunId,
            documentId,
            chunkId,
            nodeCount,
            relationshipCount
        );
        Map<String, SchemaDocument.NodeDefinition> nodeDefs = new HashMap<>();
        for (SchemaDocument.NodeDefinition def : schema.nodes()) {
            nodeDefs.put(def.label(), def);
        }
        if (result.nodes() != null) {
            for (GraphExtractionResult.ExtractedNode node : result.nodes()) {
                upsertNode(extractionRunId, schemaId, documentId, chunkId, nodeDefs.get(node.label()), node);
            }
        }
        if (result.relationships() != null) {
            for (GraphExtractionResult.ExtractedRelationship rel : result.relationships()) {
                upsertRelationship(extractionRunId, schemaId, documentId, chunkId, schema, nodeDefs, rel);
            }
        }
        log.info(
            "Graph extraction result written: runId={}, documentId={}, chunkId={}, nodes={}, relationships={}",
            extractionRunId,
            documentId,
            chunkId,
            nodeCount,
            relationshipCount
        );
    }

    private void upsertNode(
        String extractionRunId,
        String schemaId,
        String documentId,
        String chunkId,
        SchemaDocument.NodeDefinition nodeDef,
        GraphExtractionResult.ExtractedNode node
    ) {
        List<String> keyNames = NodeKeySupport.normalizedKeys(nodeDef);
        String entityId = stableNodeId(schemaId, node.label(), keyNames, node.properties());
        String label = safeToken(node.label());
        Set<String> allowedProperties = allowedNodeProperties(nodeDef);
        Map<String, Object> props = filterDeclaredProperties(node.properties(), allowedProperties, "node", node.label());
        props.put("id", entityId);
        props.put("sourceDocumentId", documentId);
        props.put("sourceChunkIds", java.util.List.of(chunkId));
        props.put("schemaId", schemaId);
        props.put("extractionRunId", extractionRunId);
        props.put("confidence", node.confidence());
        props.put("createdAt", Instant.now().toString());

        neo4jClient.query("""
            MERGE (n:%s {id: $id})
            SET n += $props
            """.formatted(label))
            .bind(entityId).to("id")
            .bind(props).to("props")
            .run();

        neo4jClient.query("""
            MATCH (r:ExtractionRun {id: $runId})
            MATCH (n:%s {id: $id})
            MATCH (c:DocumentChunk {id: $chunkId})
            MERGE (r)-[:CREATED_NODE]->(n)
            MERGE (c)-[:MENTIONS]->(n)
            """.formatted(label))
            .bind(extractionRunId).to("runId")
            .bind(entityId).to("id")
            .bind(chunkId).to("chunkId")
            .run();
    }

    private void upsertRelationship(
        String extractionRunId,
        String schemaId,
        String documentId,
        String chunkId,
        SchemaDocument schema,
        Map<String, SchemaDocument.NodeDefinition> nodeDefs,
        GraphExtractionResult.ExtractedRelationship rel
    ) {
        String fromLabel = safeToken(rel.fromLabel());
        String toLabel = safeToken(rel.toLabel());
        String type = safeToken(rel.type());
        List<String> fromKeyNames = NodeKeySupport.normalizedKeys(nodeDefs.get(rel.fromLabel()));
        List<String> toKeyNames = NodeKeySupport.normalizedKeys(nodeDefs.get(rel.toLabel()));
        String fromId = stableNodeId(schemaId, rel.fromLabel(), fromKeyNames, rel.fromKey());
        String toId = stableNodeId(schemaId, rel.toLabel(), toKeyNames, rel.toKey());
        String relId = stableRelationshipId(schemaId, rel.type(), fromId, toId);

        Set<String> allowedProperties = allowedRelationshipProperties(schema, rel.type(), rel.fromLabel(), rel.toLabel());
        Map<String, Object> props = filterDeclaredProperties(rel.properties(), allowedProperties, "relationship", rel.type());
        props.put("id", relId);
        props.put("sourceDocumentId", documentId);
        props.put("sourceChunkIds", java.util.List.of(chunkId));
        props.put("schemaId", schemaId);
        props.put("extractionRunId", extractionRunId);
        props.put("confidence", rel.confidence());
        props.put("createdAt", Instant.now().toString());

        neo4jClient.query("""
            MATCH (from:%s {id: $fromId})
            MATCH (to:%s {id: $toId})
            MERGE (from)-[r:%s {id: $relId}]->(to)
            SET r += $props
            """.formatted(fromLabel, toLabel, type))
            .bind(fromId).to("fromId")
            .bind(toId).to("toId")
            .bind(relId).to("relId")
            .bind(props).to("props")
            .run();
    }

    private String stableNodeId(String schemaId, String label, List<String> keyNames, Map<String, Object> keyProperties) {
        return GraphWriteSupport.stableNodeId(schemaId, label, keyNames, keyProperties);
    }

    private String stableRelationshipId(String schemaId, String type, String fromId, String toId) {
        return GraphWriteSupport.stableRelationshipId(schemaId, type, fromId, toId);
    }

    private Map<String, Object> filterDeclaredProperties(
        Map<String, Object> input,
        Set<String> allowed,
        String entityKind,
        String entityName
    ) {
        Map<String, Object> filtered = GraphWriteSupport.filterDeclaredProperties(input, allowed);
        Set<String> dropped = GraphWriteSupport.droppedPropertyNames(input, allowed);
        if (!dropped.isEmpty()) {
            log.warn("Dropped undeclared {} properties: {}={}", entityKind, entityName, dropped);
        }
        return filtered;
    }

    private Set<String> allowedNodeProperties(SchemaDocument.NodeDefinition nodeDef) {
        return GraphWriteSupport.allowedNodeProperties(nodeDef);
    }

    private Set<String> allowedRelationshipProperties(SchemaDocument schema, String type, String from, String to) {
        return GraphWriteSupport.allowedRelationshipProperties(schema, type, from, to);
    }

    private String safeToken(String value) {
        return GraphWriteSupport.safeToken(value);
    }
}
