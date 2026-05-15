package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import java.util.List;
import java.util.Map;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface ExtractionRunRepository extends Neo4jRepository<ExtractionRunNode, String> {

    List<ExtractionRunNode> findByDocumentIdOrderByStartedAtAsc(String documentId);

    @Query("""
        MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(run:ExtractionRun {status: 'COMPLETED'})
        RETURN count(run) > 0
        """)
    boolean hasCompletedRun(String documentId);

    @Query("""
        MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(current:ExtractionRun {id: $runId, status: 'COMPLETED'})
        OPTIONAL MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(failed:ExtractionRun {status: 'FAILED'})
        WHERE failed.id <> current.id
        OPTIONAL MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(completed:ExtractionRun {status: 'COMPLETED'})
        WHERE $allowOverwrite = true AND completed.id <> current.id
        WITH current, collect(DISTINCT failed) AS failedRunsRaw, collect(DISTINCT completed) AS completedRunsRaw
        WITH
            [run IN failedRunsRaw WHERE run IS NOT NULL] AS failedRuns,
            [run IN completedRunsRaw WHERE run IS NOT NULL] AS completedRuns
        WITH failedRuns + completedRuns AS runsToDelete
        UNWIND runsToDelete AS runToDelete
        OPTIONAL MATCH (runToDelete)-[runRel]-()
        OPTIONAL MATCH (runToDelete)-[:CREATED_NODE]->(candidateNode)
        WITH
            collect(DISTINCT runToDelete) AS runsToDeleteRaw,
            count(DISTINCT runRel) AS deletedRelationshipCount,
            collect(DISTINCT candidateNode) AS candidateNodesRaw
        WITH
            [run IN runsToDeleteRaw WHERE run IS NOT NULL] AS runsToDelete,
            deletedRelationshipCount,
            [node IN candidateNodesRaw WHERE node IS NOT NULL] AS candidateNodes
        FOREACH (run IN runsToDelete | DETACH DELETE run)
        WITH size(runsToDelete) AS deletedRuns, deletedRelationshipCount, candidateNodes
        UNWIND candidateNodes AS candidateNode
        WITH deletedRuns, deletedRelationshipCount, candidateNode
        WHERE NOT (candidateNode)--()
        WITH deletedRuns, deletedRelationshipCount, collect(DISTINCT candidateNode) AS orphanNodes
        FOREACH (orphanNode IN orphanNodes | DETACH DELETE orphanNode)
        RETURN
            deletedRuns AS deletedRuns,
            deletedRelationshipCount AS deletedRelationships,
            size(orphanNodes) AS deletedOrphanNodes
        """)
    List<Map<String, Object>> cleanupRunsAfterCompletion(String documentId, String runId, boolean allowOverwrite);
}
