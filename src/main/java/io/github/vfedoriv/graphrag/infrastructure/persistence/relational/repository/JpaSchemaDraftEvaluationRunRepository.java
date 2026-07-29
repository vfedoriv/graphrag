package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftEvaluationRunEntity;
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

public interface JpaSchemaDraftEvaluationRunRepository
    extends JpaRepository<SchemaDraftEvaluationRunEntity, String> {
    Optional<SchemaDraftEvaluationRunEntity> findByIdAndDraftId(String id, String draftId);
    List<SchemaDraftEvaluationRunEntity> findByStatusIn(List<SchemaDraftEvaluationStatus> statuses);
    List<SchemaDraftEvaluationRunEntity> findByDraftIdOrderByCreatedAtDescIdDesc(String draftId);
    Optional<SchemaDraftEvaluationRunEntity> findFirstByDraftIdAndStatusOrderByCreatedAtDescIdDesc(
        String draftId, SchemaDraftEvaluationStatus status);
    Page<SchemaDraftEvaluationRunEntity> findByDraftId(String draftId, Pageable pageable);

    @Query("""
        select run from SchemaDraftEvaluationRunEntity run
        where run.draftId in :draftIds
          and not exists (
            select newer.id from SchemaDraftEvaluationRunEntity newer
            where newer.draftId = run.draftId
              and (newer.createdAt > run.createdAt
                or (newer.createdAt = run.createdAt and newer.id > run.id))
          )
        """)
    List<SchemaDraftEvaluationRunEntity> findLatestForDraftIds(@Param("draftIds") List<String> draftIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query("""
        update SchemaDraftEvaluationRunEntity run
        set run.status = io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus.RUNNING,
            run.claimedBy = :workerId, run.claimedAt = :claimedAt, run.claimUntil = :claimUntil,
            run.startedAt = :claimedAt, run.persistenceVersion = run.persistenceVersion + 1
        where run.id = :runId
          and run.status = io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus.QUEUED
          and run.claimedBy is null
        """)
    int claim(
        @Param("runId") String runId, @Param("workerId") String workerId,
        @Param("claimedAt") Instant claimedAt, @Param("claimUntil") Instant claimUntil
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query("""
        update SchemaDraftEvaluationRunEntity run
        set run.status = io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus.INTERRUPTED,
            run.failureCategory = 'CLAIM_EXPIRED', run.retryable = true,
            run.completedAt = :completedAt, run.persistenceVersion = run.persistenceVersion + 1
        where run.id = :runId
          and run.status = io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus.RUNNING
          and run.claimedBy = :claimedBy and run.claimUntil < :now
        """)
    int interruptExpired(
        @Param("runId") String runId, @Param("claimedBy") String claimedBy,
        @Param("now") Instant now, @Param("completedAt") Instant completedAt
    );
}
