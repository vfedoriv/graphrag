package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchemaDraftAnalysisRunRepository extends Neo4jRepository<SchemaDraftAnalysisRunNode, String> {
    Optional<SchemaDraftAnalysisRunNode> findFirstByDraftIdAndStatusOrderByCreatedAtDesc(
        String draftId, SchemaDraftAnalysisStatus status
    );
    List<SchemaDraftAnalysisRunNode> findByStatus(SchemaDraftAnalysisStatus status);
    List<SchemaDraftAnalysisRunNode> findByDraftIdOrderByCreatedAtDesc(String draftId);
    Optional<SchemaDraftAnalysisRunNode> findByIdAndDraftId(String id, String draftId);

    @Query(value = """
        MATCH (r:SchemaDraftAnalysisRun {draftId: $draftId})
        RETURN r ORDER BY r.createdAt DESC, r.id DESC SKIP $skip LIMIT $limit
        """, countQuery = """
        MATCH (r:SchemaDraftAnalysisRun {draftId: $draftId}) RETURN count(r)
        """)
    Page<SchemaDraftAnalysisRunNode> findPageByDraftId(String draftId, Pageable pageable);

    @Query("""
        MATCH (d:SchemaDraft) WHERE d.id IN $draftIds
        MATCH (r:SchemaDraftAnalysisRun {draftId: d.id})
        WHERE r.id = d.runningAnalysisRunId OR r.aggregateRevisionId = d.currentAggregateId
        WITH d, r ORDER BY
            CASE WHEN r.id = d.runningAnalysisRunId THEN 0 ELSE 1 END,
            r.createdAt DESC, r.id DESC
        WITH d, collect(r)[0] AS current
        RETURN current
        """)
    List<SchemaDraftAnalysisRunNode> findCurrentForDraftIds(List<String> draftIds);

    @Query("""
        MATCH (r:SchemaDraftAnalysisRun {id: $runId, status: 'RUNNING'})
        WHERE r.claimedBy IS NULL
        SET r.claimedBy = $workerId, r.claimedAt = $claimedAt, r.persistenceVersion = coalesce(r.persistenceVersion, 0) + 1
        RETURN count(r)
        """)
    Long claim(String runId, String workerId, Instant claimedAt);
}
