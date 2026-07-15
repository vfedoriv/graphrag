package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface SchemaDraftEvaluationRunRepository extends Neo4jRepository<SchemaDraftEvaluationRunNode, String> {
    Optional<SchemaDraftEvaluationRunNode> findByIdAndDraftId(String id, String draftId);
    List<SchemaDraftEvaluationRunNode> findByStatusIn(List<SchemaDraftEvaluationStatus> statuses);
    List<SchemaDraftEvaluationRunNode> findByDraftIdOrderByCreatedAtDesc(String draftId);
    Optional<SchemaDraftEvaluationRunNode> findFirstByDraftIdAndStatusOrderByCreatedAtDesc(
        String draftId, SchemaDraftEvaluationStatus status);

    @Query("""
        MATCH (r:SchemaDraftEvaluationRun {id: $runId, status: 'QUEUED'})
        WHERE r.claimedBy IS NULL
        SET r.status = 'RUNNING', r.claimedBy = $workerId, r.claimedAt = $claimedAt,
            r.startedAt = $claimedAt, r.persistenceVersion = coalesce(r.persistenceVersion, 0) + 1
        RETURN count(r)
        """)
    Long claim(String runId, String workerId, Instant claimedAt);
}
