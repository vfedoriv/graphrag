package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface DocumentProcessingRunRepository extends Neo4jRepository<DocumentProcessingRunNode, String> {

    List<DocumentProcessingRunNode> findByDocumentIdOrderByStartedAtAsc(String documentId);

    @Query("""
        MATCH (:DocumentUpload {id: $documentId})-[:HAS_PROCESSING_RUN]->(run:DocumentProcessingRun)
        RETURN count(run) > 0
        """)
    Boolean existsLinkedToDocument(String documentId);

    @Query("""
        MATCH (document:DocumentUpload {id: $documentId})
        MATCH (run:DocumentProcessingRun {id: $runId})
        MERGE (document)-[:HAS_PROCESSING_RUN]->(run)
        RETURN count(run)
        """)
    Long attachToDocument(String documentId, String runId);

    @Query("""
        MATCH (:DocumentUpload {id: $documentId})-[:HAS_PROCESSING_RUN]->(run:DocumentProcessingRun)
        WHERE run.status = 'COMPLETED' AND run.id <> $activeRunId
        SET run.activeCompleted = false
        RETURN count(run)
        """)
    Long deactivateOtherCompletedRuns(String documentId, String activeRunId);
}
