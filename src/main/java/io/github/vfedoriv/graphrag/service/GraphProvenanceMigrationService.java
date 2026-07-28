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
        long scopedChunks = backfillLegacyChunkScope();
        long scopedEvidence = backfillLegacyEvidenceScope();
        long migratedNodeEvidence = migrateLegacyNodeEvidence();
        long migratedRelationshipEvidence = migrateLegacyRelationshipEvidence();
        MigrationValidation validation = validation();
        log.info(
            "Graph provenance migration complete: scopedChunks={}, scopedEvidence={}, nodeEvidence={}, relationshipEvidence={}, legacyNodes={}, legacyRelationships={}, nodeEvidenceLinks={}, relationshipEvidenceRecords={}",
            scopedChunks,
            scopedEvidence,
            migratedNodeEvidence,
            migratedRelationshipEvidence,
            validation.legacyNodes(),
            validation.legacyRelationships(),
            validation.nodeEvidenceLinks(),
            validation.relationshipEvidenceRecords()
        );
    }

    private void ensureIndexes() {
        neo4jClient.query("CREATE CONSTRAINT document_chunk_id IF NOT EXISTS FOR (c:DocumentChunk) REQUIRE c.id IS UNIQUE").run();
        neo4jClient.query("CREATE CONSTRAINT graph_extraction_evidence_id IF NOT EXISTS FOR (e:GraphExtractionEvidence) REQUIRE e.id IS UNIQUE").run();
        neo4jClient.query("CREATE INDEX document_chunk_knowledge_base IF NOT EXISTS FOR (c:DocumentChunk) ON (c.knowledgeBaseId)").run();
        neo4jClient.query("CREATE INDEX document_chunk_document IF NOT EXISTS FOR (c:DocumentChunk) ON (c.documentId)").run();
        neo4jClient.query("CREATE INDEX document_chunk_scope_space IF NOT EXISTS FOR (c:DocumentChunk) ON (c.knowledgeBaseId, c.embeddingSpaceId)").run();
        neo4jClient.query("CREATE INDEX graph_extraction_evidence_knowledge_base IF NOT EXISTS FOR (e:GraphExtractionEvidence) ON (e.knowledgeBaseId)").run();
        neo4jClient.query("CREATE INDEX graph_extraction_evidence_document IF NOT EXISTS FOR (e:GraphExtractionEvidence) ON (e.sourceDocumentId)").run();
        neo4jClient.query("CREATE INDEX graph_extraction_evidence_run IF NOT EXISTS FOR (e:GraphExtractionEvidence) ON (e.extractionRunId)").run();
        neo4jClient.query("CREATE INDEX graph_extraction_evidence_fact IF NOT EXISTS FOR (e:GraphExtractionEvidence) ON (e.canonicalFactId)").run();
        neo4jClient.query("CREATE INDEX graph_extraction_evidence_chunk IF NOT EXISTS FOR (e:GraphExtractionEvidence) ON (e.sourceChunkId)").run();
    }

    private long backfillLegacyChunkScope() {
        return executeCount("""
            MATCH (document:DocumentUpload)-[:HAS_CHUNK]->(chunk:DocumentChunk)
            WHERE chunk.knowledgeBaseId IS NULL
              AND document.knowledgeBaseId IS NOT NULL
            SET chunk.knowledgeBaseId = document.knowledgeBaseId,
                chunk.documentId = coalesce(chunk.documentId, document.id)
            RETURN count(chunk) AS count
            """);
    }

    private long backfillLegacyEvidenceScope() {
        return executeCount("""
            MATCH (evidence:GraphExtractionEvidence)
            WHERE evidence.knowledgeBaseId IS NULL
            OPTIONAL MATCH (chunk:DocumentChunk {id: evidence.sourceChunkId})
            WITH evidence, chunk
            WHERE chunk.knowledgeBaseId IS NOT NULL
            SET evidence.knowledgeBaseId = chunk.knowledgeBaseId
            RETURN count(evidence) AS count
            """);
    }

    private long migrateLegacyNodeEvidence() {
        return executeCount("""
            MATCH (node)
            WHERE node.id STARTS WITH 'node:'
                AND node.sourceDocumentId IS NOT NULL
                AND node.extractionRunId IS NOT NULL
            WITH node, coalesce(node.sourceChunkIds[0], 'legacy-chunk:' + node.id) AS chunkId
            OPTIONAL MATCH (chunk:DocumentChunk {id: chunkId})
            WITH node, chunkId, chunk
            WHERE chunk.knowledgeBaseId IS NOT NULL
            MERGE (e:GraphExtractionEvidence:NodeExtractionEvidence {
                id: 'legacy-node:' + node.id + ':' + node.extractionRunId + ':' + chunkId
            })
            ON CREATE SET
                e.factKind = 'NODE',
                e.canonicalFactId = node.id,
                e.knowledgeBaseId = chunk.knowledgeBaseId,
                e.sourceDocumentId = node.sourceDocumentId,
                e.sourceChunkId = chunkId,
                e.sourceChunkIds = [chunkId],
                e.schemaId = node.schemaId,
                e.extractionRunId = node.extractionRunId,
                e.confidence = node.confidence,
                e.createdAt = coalesce(node.createdAt, toString(datetime())),
                e.legacyProvenance = true
            MERGE (e)-[:ASSERTS_NODE]->(node)
            MERGE (chunk)-[:HAS_GRAPH_EVIDENCE]->(e)
            RETURN count(e) AS count
            """);
    }

    private long migrateLegacyRelationshipEvidence() {
        return executeCount("""
            MATCH ()-[relationship]->()
            WHERE relationship.id STARTS WITH 'rel:'
                AND relationship.sourceDocumentId IS NOT NULL
                AND relationship.extractionRunId IS NOT NULL
            WITH relationship, startNode(relationship) AS source, endNode(relationship) AS target,
                coalesce(relationship.sourceChunkIds[0], 'legacy-chunk:' + relationship.id) AS chunkId
            OPTIONAL MATCH (chunk:DocumentChunk {id: chunkId})
            WITH relationship, source, target, chunkId, chunk
            WHERE chunk.knowledgeBaseId IS NOT NULL
            MERGE (e:GraphExtractionEvidence:RelationshipExtractionEvidence {
                id: 'legacy-relationship:' + relationship.id + ':' + relationship.extractionRunId + ':' + chunkId
            })
            ON CREATE SET
                e.factKind = 'RELATIONSHIP',
                e.canonicalFactId = relationship.id,
                e.knowledgeBaseId = chunk.knowledgeBaseId,
                e.sourceDocumentId = relationship.sourceDocumentId,
                e.sourceChunkId = chunkId,
                e.sourceChunkIds = [chunkId],
                e.schemaId = relationship.schemaId,
                e.extractionRunId = relationship.extractionRunId,
                e.confidence = relationship.confidence,
                e.createdAt = coalesce(relationship.createdAt, toString(datetime())),
                e.legacyProvenance = true
            MERGE (chunk)-[:HAS_GRAPH_EVIDENCE]->(e)
            MERGE (e)-[:ASSERTS_FROM]->(source)
            MERGE (e)-[:ASSERTS_TO]->(target)
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
