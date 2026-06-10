package io.github.vfedoriv.graphrag.service;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GraphArtifactCleanupService {

    private final Neo4jClient neo4jClient;

    public GraphArtifactCleanupService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public DocumentArtifactCleanupResult cleanupDocumentArtifacts(String documentId) {
        Map<String, Object> cleanupRow = neo4jClient.query("""
            MATCH (document:DocumentUpload {id: $documentId})
            OPTIONAL MATCH (document)-[:HAS_CHUNK]->(chunk:DocumentChunk)
            WITH document, collect(DISTINCT chunk) AS chunks
            FOREACH (chunk IN chunks | DETACH DELETE chunk)
            WITH document, size(chunks) AS deletedChunks
            OPTIONAL MATCH (document)-[:HAS_EXTRACTION_RUN]->(run:ExtractionRun)
            WITH document, deletedChunks, [run IN collect(DISTINCT run) WHERE run IS NOT NULL] AS runsToDelete
            WITH document, deletedChunks, runsToDelete, [run IN runsToDelete | run.id] AS runIds
            OPTIONAL MATCH ()-[graphRel]-()
            WHERE graphRel.sourceDocumentId = $documentId
                AND (size(runIds) = 0 OR graphRel.extractionRunId IN runIds)
            WITH document, deletedChunks, runsToDelete, collect(DISTINCT graphRel) AS graphRelationships
            FOREACH (graphRel IN graphRelationships | DELETE graphRel)
            WITH document, deletedChunks, runsToDelete, size(graphRelationships) AS deletedGraphRelationshipCount
            UNWIND CASE WHEN size(runsToDelete) = 0 THEN [null] ELSE runsToDelete END AS runToDelete
            OPTIONAL MATCH (runToDelete)-[runRel]-()
            WITH document, deletedChunks, runsToDelete, deletedGraphRelationshipCount, count(DISTINCT runRel) AS deletedRunRelationshipCount
            FOREACH (run IN runsToDelete | DETACH DELETE run)
            WITH
                document,
                deletedChunks,
                size(runsToDelete) AS deletedRuns,
                deletedGraphRelationshipCount + deletedRunRelationshipCount AS deletedRelationshipCount
            OPTIONAL MATCH (obsoleteNode)
            WHERE obsoleteNode.sourceDocumentId = $documentId
                AND NOT obsoleteNode:ExtractionRun
                AND NOT obsoleteNode:DocumentUpload
                AND NOT obsoleteNode:DocumentChunk
                AND NOT obsoleteNode:KnowledgeBase
                AND NOT obsoleteNode:SchemaDefinition
                AND NOT (obsoleteNode)<-[:CREATED_NODE]-(:ExtractionRun)
            WITH deletedChunks, deletedRuns, deletedRelationshipCount, collect(DISTINCT obsoleteNode) AS obsoleteNodes
            FOREACH (obsoleteNode IN obsoleteNodes | DETACH DELETE obsoleteNode)
            RETURN
                deletedChunks AS deletedChunks,
                deletedRuns AS deletedRuns,
                deletedRelationshipCount AS deletedRelationships,
                size(obsoleteNodes) AS deletedObsoleteExtractedNodes
            """)
            .bind(documentId).to("documentId")
            .fetch()
            .one()
            .orElse(null);
        DocumentArtifactCleanupResult result = documentCleanupResult(cleanupRow);
        if (cleanupRow == null) {
            log.warn("Document cleanup returned no row: documentId={}", documentId);
        }
        return result;
    }

    public ExtractionRunCleanupResult cleanupRunsAfterSuccessfulExtraction(String documentId, String runId, boolean allowOverwrite) {
        Map<String, Object> cleanupRow = neo4jClient.query("""
            MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(current:ExtractionRun {id: $runId, status: 'COMPLETED'})
            OPTIONAL MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(failed:ExtractionRun {status: 'FAILED'})
            WHERE failed.id <> current.id
            OPTIONAL MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(completed:ExtractionRun {status: 'COMPLETED'})
            WHERE $allowOverwrite = true AND completed.id <> current.id
            WITH [run IN collect(DISTINCT failed) WHERE run IS NOT NULL] AS failedRuns,
                 [run IN collect(DISTINCT completed) WHERE run IS NOT NULL] AS completedRuns
            WITH failedRuns + completedRuns AS runsToDelete
            WITH runsToDelete, [run IN runsToDelete | run.id] AS runIds
            OPTIONAL MATCH ()-[graphRel]-()
            WHERE graphRel.sourceDocumentId = $documentId AND graphRel.extractionRunId IN runIds
            WITH runsToDelete, runIds, collect(DISTINCT graphRel) AS graphRelationships
            FOREACH (graphRel IN graphRelationships | DELETE graphRel)
            WITH runsToDelete, runIds, size(graphRelationships) AS deletedGraphRelationshipCount
            UNWIND CASE WHEN size(runsToDelete) = 0 THEN [null] ELSE runsToDelete END AS runToDelete
            OPTIONAL MATCH (runToDelete)-[runRel]-()
            WITH runsToDelete, runIds, deletedGraphRelationshipCount, count(DISTINCT runRel) AS deletedRunRelationshipCount
            FOREACH (run IN runsToDelete | DETACH DELETE run)
            WITH size(runsToDelete) AS deletedRuns, deletedGraphRelationshipCount + deletedRunRelationshipCount AS deletedRelationshipCount
            OPTIONAL MATCH (obsoleteNode)
            WHERE deletedRuns > 0
                AND obsoleteNode.sourceDocumentId = $documentId
                AND NOT obsoleteNode:ExtractionRun
                AND NOT obsoleteNode:DocumentUpload
                AND NOT obsoleteNode:DocumentChunk
                AND NOT obsoleteNode:KnowledgeBase
                AND NOT obsoleteNode:SchemaDefinition
                AND NOT (obsoleteNode)<-[:CREATED_NODE]-(:ExtractionRun)
            WITH deletedRuns, deletedRelationshipCount, collect(DISTINCT obsoleteNode) AS obsoleteNodes
            FOREACH (obsoleteNode IN obsoleteNodes | DETACH DELETE obsoleteNode)
            RETURN
                deletedRuns AS deletedRuns,
                deletedRelationshipCount AS deletedRelationships,
                size(obsoleteNodes) AS deletedObsoleteExtractedNodes
            """)
            .bind(documentId).to("documentId")
            .bind(runId).to("runId")
            .bind(allowOverwrite).to("allowOverwrite")
            .fetch()
            .one()
            .orElse(null);
        ExtractionRunCleanupResult result = extractionRunCleanupResult(cleanupRow);
        if (cleanupRow == null) {
            log.warn("Cleanup returned no row: runId={}, documentId={}", runId, documentId);
        }
        return result;
    }

    static DocumentArtifactCleanupResult documentCleanupResult(Map<String, Object> cleanupRow) {
        if (cleanupRow == null) {
            return DocumentArtifactCleanupResult.zero();
        }
        return new DocumentArtifactCleanupResult(
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedChunks")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedRuns")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedRelationships")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedObsoleteExtractedNodes"))
        );
    }

    static ExtractionRunCleanupResult extractionRunCleanupResult(Map<String, Object> cleanupRow) {
        if (cleanupRow == null) {
            return ExtractionRunCleanupResult.zero();
        }
        return new ExtractionRunCleanupResult(
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedRuns")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedRelationships")),
            GraphExtractionCleanupSupport.toLong(cleanupRow.get("deletedObsoleteExtractedNodes"))
        );
    }

    public record DocumentArtifactCleanupResult(
        long deletedChunks,
        long deletedRuns,
        long deletedRelationships,
        long deletedObsoleteExtractedNodes
    ) {
        public static DocumentArtifactCleanupResult zero() {
            return new DocumentArtifactCleanupResult(0L, 0L, 0L, 0L);
        }
    }

    public record ExtractionRunCleanupResult(
        long deletedRuns,
        long deletedRelationships,
        long deletedObsoleteExtractedNodes
    ) {
        public static ExtractionRunCleanupResult zero() {
            return new ExtractionRunCleanupResult(0L, 0L, 0L);
        }
    }
}
