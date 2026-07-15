package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchemaDraftEvaluationRunRepository extends Neo4jRepository<SchemaDraftEvaluationRunNode, String> {
    Optional<SchemaDraftEvaluationRunNode> findByIdAndDraftId(String id, String draftId);
    List<SchemaDraftEvaluationRunNode> findByStatusIn(List<SchemaDraftEvaluationStatus> statuses);
    List<SchemaDraftEvaluationRunNode> findByDraftIdOrderByCreatedAtDesc(String draftId);
    Optional<SchemaDraftEvaluationRunNode> findFirstByDraftIdAndStatusOrderByCreatedAtDesc(
        String draftId, SchemaDraftEvaluationStatus status);

    @Query(value = """
        MATCH (r:SchemaDraftEvaluationRun {draftId: $draftId})
        RETURN r ORDER BY r.createdAt DESC, r.id DESC SKIP $skip LIMIT $limit
        """, countQuery = """
        MATCH (r:SchemaDraftEvaluationRun {draftId: $draftId}) RETURN count(r)
        """)
    Page<SchemaDraftEvaluationRunNode> findPageByDraftId(String draftId, Pageable pageable);

    @Query("""
        MATCH (r:SchemaDraftEvaluationRun) WHERE r.draftId IN $draftIds
        AND NOT EXISTS {
            MATCH (newer:SchemaDraftEvaluationRun {draftId: r.draftId})
            WHERE newer.createdAt > r.createdAt
               OR (newer.createdAt = r.createdAt AND newer.id > r.id)
        }
        RETURN r
        """)
    List<SchemaDraftEvaluationRunNode> findLatestForDraftIds(List<String> draftIds);

    @Query("""
        MATCH (r:SchemaDraftEvaluationRun {id: $runId, status: 'QUEUED'})
        WHERE r.claimedBy IS NULL
        SET r.status = 'RUNNING', r.claimedBy = $workerId, r.claimedAt = $claimedAt,
            r.startedAt = $claimedAt, r.persistenceVersion = coalesce(r.persistenceVersion, 0) + 1
        RETURN count(r)
        """)
    Long claim(String runId, String workerId, Instant claimedAt);
}
