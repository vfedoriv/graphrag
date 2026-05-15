package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import java.util.List;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface ExtractionRunRepository extends Neo4jRepository<ExtractionRunNode, String> {

    List<ExtractionRunNode> findByDocumentIdOrderByStartedAtAsc(String documentId);

    @Query("""
        MATCH (:DocumentUpload {id: $documentId})-[:HAS_EXTRACTION_RUN]->(run:ExtractionRun {status: 'COMPLETED'})
        RETURN count(run) > 0
        """)
    boolean hasCompletedRun(String documentId);

}
