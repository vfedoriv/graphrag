package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftAnalysisRunEntity;
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

public interface JpaSchemaDraftAnalysisRunRepository
    extends JpaRepository<SchemaDraftAnalysisRunEntity, String> {
    Optional<SchemaDraftAnalysisRunEntity> findFirstByDraftIdAndStatusOrderByCreatedAtDescIdDesc(
        String draftId, SchemaDraftAnalysisStatus status);
    List<SchemaDraftAnalysisRunEntity> findByStatus(SchemaDraftAnalysisStatus status);
    List<SchemaDraftAnalysisRunEntity> findByDraftIdOrderByCreatedAtDescIdDesc(String draftId);
    Optional<SchemaDraftAnalysisRunEntity> findByIdAndDraftId(String id, String draftId);
    Page<SchemaDraftAnalysisRunEntity> findByDraftId(String draftId, Pageable pageable);

    @Query("""
        select run from SchemaDraftAnalysisRunEntity run
        join SchemaDraftEntity draft on draft.id = run.draftId
        where draft.id in :draftIds
          and (run.id = draft.runningAnalysisRunId or run.aggregateRevisionId = draft.currentAggregateId)
        order by run.draftId asc,
          case when run.id = draft.runningAnalysisRunId then 0 else 1 end asc,
          run.createdAt desc, run.id desc
        """)
    List<SchemaDraftAnalysisRunEntity> findCurrentForDraftIds(@Param("draftIds") List<String> draftIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query("""
        update SchemaDraftAnalysisRunEntity run
        set run.claimedBy = :workerId, run.claimedAt = :claimedAt,
            run.persistenceVersion = run.persistenceVersion + 1
        where run.id = :runId
          and run.status = io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus.RUNNING
          and run.claimedBy is null
        """)
    int claim(
        @Param("runId") String runId,
        @Param("workerId") String workerId,
        @Param("claimedAt") Instant claimedAt
    );
}
