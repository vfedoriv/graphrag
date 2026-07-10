package io.github.vfedoriv.graphrag.service;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class GraphProvenanceMigrationService implements ApplicationRunner {

    private final Neo4jClient neo4jClient;

    public GraphProvenanceMigrationService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIndexes();
        long migratedNodeEvidence = migrateLegacyNodeEvidence();
        long migratedRelationshipEvidence = migrateLegacyRelationshipEvidence();
        MigrationValidation validation = validation();
        log.info(
            "Graph provenance migration complete: nodeEvidence={}, relationshipEvidence={}, legacyNodes={}, legacyRelationships={}, nodeEvidenceLinks={}, relationshipEvidenceRecords={}",
            migratedNodeEvidence,
            migratedRelationshipEvidence,
            validation.legacyNodes(),
            validation.legacyRelationships(),
            validation.nodeEvidenceLinks(),
            validation.relationshipEvidenceRecords()
        );
    }

    private void ensureIndexes() {
        neo4jClient.query("CREATE INDEX graph_extraction_evidence_id IF NOT EXISTS FOR (e:GraphExtractionEvidence) ON (e.id)").run();
        neo4jClient.query("CREATE INDEX graph_extraction_evidence_document IF NOT EXISTS FOR (e:GraphExtractionEvidence) ON (e.sourceDocumentId)").run();
        neo4jClient.query("CREATE INDEX graph_extraction_evidence_run IF NOT EXISTS FOR (e:GraphExtractionEvidence) ON (e.extractionRunId)").run();
        neo4jClient.query("CREATE INDEX graph_extraction_evidence_fact IF NOT EXISTS FOR (e:GraphExtractionEvidence) ON (e.canonicalFactId)").run();
    }

    private long migrateLegacyNodeEvidence() {
        return executeCount("""
            MATCH (node)
            WHERE node.id STARTS WITH 'node:'
                AND node.sourceDocumentId IS NOT NULL
                AND node.extractionRunId IS NOT NULL
            WITH node, coalesce(node.sourceChunkIds[0], 'legacy-chunk:' + node.id) AS chunkId
            MERGE (e:GraphExtractionEvidence:NodeExtractionEvidence {
                id: 'legacy-node:' + node.id + ':' + node.extractionRunId + ':' + chunkId
            })
            ON CREATE SET
                e.factKind = 'NODE',
                e.canonicalFactId = node.id,
                e.sourceDocumentId = node.sourceDocumentId,
                e.sourceChunkId = chunkId,
                e.sourceChunkIds = [chunkId],
                e.schemaId = node.schemaId,
                e.extractionRunId = node.extractionRunId,
                e.confidence = node.confidence,
                e.createdAt = coalesce(node.createdAt, toString(datetime())),
                e.legacyProvenance = true
            MERGE (e)-[:ASSERTS_NODE]->(node)
            RETURN count(e) AS count
            """);
    }

    private long migrateLegacyRelationshipEvidence() {
        return executeCount("""
            MATCH ()-[relationship]->()
            WHERE relationship.id STARTS WITH 'rel:'
                AND relationship.sourceDocumentId IS NOT NULL
                AND relationship.extractionRunId IS NOT NULL
            WITH relationship, coalesce(relationship.sourceChunkIds[0], 'legacy-chunk:' + relationship.id) AS chunkId
            MERGE (e:GraphExtractionEvidence:RelationshipExtractionEvidence {
                id: 'legacy-relationship:' + relationship.id + ':' + relationship.extractionRunId + ':' + chunkId
            })
            ON CREATE SET
                e.factKind = 'RELATIONSHIP',
                e.canonicalFactId = relationship.id,
                e.sourceDocumentId = relationship.sourceDocumentId,
                e.sourceChunkId = chunkId,
                e.sourceChunkIds = [chunkId],
                e.schemaId = relationship.schemaId,
                e.extractionRunId = relationship.extractionRunId,
                e.confidence = relationship.confidence,
                e.createdAt = coalesce(relationship.createdAt, toString(datetime())),
                e.legacyProvenance = true
            RETURN count(e) AS count
            """);
    }

    private MigrationValidation validation() {
        Map<String, Object> row = neo4jClient.query("""
            OPTIONAL MATCH (legacyNode)
            WHERE legacyNode.id STARTS WITH 'node:'
                AND legacyNode.sourceDocumentId IS NOT NULL
                AND legacyNode.extractionRunId IS NOT NULL
            WITH count(legacyNode) AS legacyNodes
            OPTIONAL MATCH ()-[legacyRelationship]->()
            WHERE legacyRelationship.id STARTS WITH 'rel:'
                AND legacyRelationship.sourceDocumentId IS NOT NULL
                AND legacyRelationship.extractionRunId IS NOT NULL
            WITH legacyNodes, count(legacyRelationship) AS legacyRelationships
            OPTIONAL MATCH (:GraphExtractionEvidence:NodeExtractionEvidence)-[:ASSERTS_NODE]->()
            WITH legacyNodes, legacyRelationships, count(*) AS nodeEvidenceLinks
            OPTIONAL MATCH (:GraphExtractionEvidence:RelationshipExtractionEvidence)
            RETURN legacyNodes, legacyRelationships, nodeEvidenceLinks, count(*) AS relationshipEvidenceRecords
            """)
            .fetch()
            .one()
            .orElse(Map.of());
        return new MigrationValidation(
            GraphExtractionCleanupSupport.toLong(row.get("legacyNodes")),
            GraphExtractionCleanupSupport.toLong(row.get("legacyRelationships")),
            GraphExtractionCleanupSupport.toLong(row.get("nodeEvidenceLinks")),
            GraphExtractionCleanupSupport.toLong(row.get("relationshipEvidenceRecords"))
        );
    }

    private long executeCount(String cypher) {
        Map<String, Object> row = neo4jClient.query(cypher).fetch().one().orElse(Map.of());
        return GraphExtractionCleanupSupport.toLong(row.get("count"));
    }

    record MigrationValidation(
        long legacyNodes,
        long legacyRelationships,
        long nodeEvidenceLinks,
        long relationshipEvidenceRecords
    ) {
    }
}
