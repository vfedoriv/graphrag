package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import java.util.List;
import java.util.Map;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface ExtractionRunRepository extends Neo4jRepository<ExtractionRunNode, String> {

    List<ExtractionRunNode> findByDocumentIdOrderByStartedAtAsc(String documentId);

    @Query("""
        MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(current:ExtractionRun {id: $runId, status: 'COMPLETED'})
        OPTIONAL MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(failed:ExtractionRun {status: 'FAILED'})
        WHERE failed.id <> current.id
        OPTIONAL MATCH (failed)-[failedRel]-()
        OPTIONAL MATCH (failed)-[:CREATED_NODE]->(candidateNode)
        WITH
            collect(DISTINCT failed) AS failedRunsRaw,
            count(DISTINCT failedRel) AS failedRelationshipCount,
            collect(DISTINCT candidateNode) AS candidateNodesRaw
        WITH
            [run IN failedRunsRaw WHERE run IS NOT NULL] AS failedRuns,
            failedRelationshipCount,
            [node IN candidateNodesRaw WHERE node IS NOT NULL] AS candidateNodes
        FOREACH (run IN failedRuns | DETACH DELETE run)
        WITH size(failedRuns) AS deletedRuns, failedRelationshipCount, candidateNodes
        UNWIND candidateNodes AS candidateNode
        WITH deletedRuns, failedRelationshipCount, candidateNode
        WHERE NOT (candidateNode)--()
        WITH deletedRuns, failedRelationshipCount, collect(DISTINCT candidateNode) AS orphanNodes
        FOREACH (orphanNode IN orphanNodes | DETACH DELETE orphanNode)
        RETURN
            deletedRuns AS deletedRuns,
            failedRelationshipCount AS deletedRelationships,
            size(orphanNodes) AS deletedOrphanNodes
        """)
    List<Map<String, Object>> cleanupFailedRunsAfterCompletion(String documentId, String runId);
}
