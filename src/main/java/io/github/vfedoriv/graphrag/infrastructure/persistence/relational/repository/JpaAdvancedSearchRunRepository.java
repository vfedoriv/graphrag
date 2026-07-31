package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.AdvancedSearchRunEntity;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAdvancedSearchRunRepository extends JpaRepository<AdvancedSearchRunEntity, String> {
    Optional<AdvancedSearchRunEntity> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);
    Page<AdvancedSearchRunEntity> findByKnowledgeBaseId(String knowledgeBaseId, Pageable pageable);
    Page<AdvancedSearchRunEntity> findByKnowledgeBaseIdAndStatus(
        String knowledgeBaseId, AdvancedSearchRunStatus status, Pageable pageable);
    List<AdvancedSearchRunEntity> findByStatusIn(List<AdvancedSearchRunStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query("""
        update AdvancedSearchRunEntity run set run.status = io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus.RUNNING,
          run.stage = io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStage.RETRIEVAL,
          run.claimedBy = :workerId, run.claimedAt = :now, run.startedAt = :now,
          run.persistenceVersion = run.persistenceVersion + 1
        where run.id = :runId and run.status = io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus.QUEUED
          and run.cancellationRequestedAt is null
        """)
    int claim(@Param("runId") String runId, @Param("workerId") String workerId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query("""
        update AdvancedSearchRunEntity run set run.status = io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus.CANCELLED,
          run.stage = io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStage.TERMINAL,
          run.cancellationRequestedAt = :now, run.failureCategory = 'CANCELLED',
          run.completedAt = :now, run.expiresAt = :expiresAt,
          run.persistenceVersion = run.persistenceVersion + 1
        where run.id = :runId and run.knowledgeBaseId = :knowledgeBaseId
          and run.status = io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus.QUEUED
        """)
    int cancelQueued(
        @Param("runId") String runId, @Param("knowledgeBaseId") String knowledgeBaseId,
        @Param("now") Instant now, @Param("expiresAt") Instant expiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query("""
        update AdvancedSearchRunEntity run set run.cancellationRequestedAt = :now,
          run.persistenceVersion = run.persistenceVersion + 1
        where run.id = :runId and run.knowledgeBaseId = :knowledgeBaseId
          and run.status = io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus.RUNNING
          and run.cancellationRequestedAt is null
        """)
    int requestRunningCancellation(
        @Param("runId") String runId, @Param("knowledgeBaseId") String knowledgeBaseId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query(value = """
        DELETE FROM app.advanced_search_run WHERE id IN (
          SELECT id FROM app.advanced_search_run
          WHERE expires_at < :now AND status IN ('COMPLETED','PARTIAL','FAILED','CANCELLED','INTERRUPTED')
          ORDER BY expires_at, id LIMIT :batchSize
        )
        """, nativeQuery = true)
    int deleteExpired(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
