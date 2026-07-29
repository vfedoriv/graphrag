package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaReprocessingPlanEntity;
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

public interface JpaSchemaReprocessingPlanRepository extends JpaRepository<SchemaReprocessingPlanEntity, String> {
    Optional<SchemaReprocessingPlanEntity> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);
    List<SchemaReprocessingPlanEntity> findByStatusIn(List<SchemaReprocessingPlanStatus> statuses);
    Page<SchemaReprocessingPlanEntity> findByKnowledgeBaseId(String knowledgeBaseId, Pageable pageable);
    Page<SchemaReprocessingPlanEntity> findByKnowledgeBaseIdAndDraftId(
        String knowledgeBaseId, String draftId, Pageable pageable);

    @Query("""
        select plan from SchemaReprocessingPlanEntity plan
        where plan.draftId in :draftIds
          and not exists (
            select newer.id from SchemaReprocessingPlanEntity newer
            where newer.draftId = plan.draftId
              and (newer.createdAt > plan.createdAt
                or (newer.createdAt = plan.createdAt and newer.id > plan.id))
          )
        """)
    List<SchemaReprocessingPlanEntity> findLatestForDraftIds(@Param("draftIds") List<String> draftIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query("""
        update SchemaReprocessingPlanEntity plan
        set plan.status = io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus.RUNNING,
            plan.claimedBy = :workerId, plan.claimedAt = :claimedAt, plan.claimUntil = :claimUntil,
            plan.startedAt = :claimedAt, plan.persistenceVersion = plan.persistenceVersion + 1
        where plan.id = :planId
          and plan.status = io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus.QUEUED
          and plan.claimedBy is null
        """)
    int claim(
        @Param("planId") String planId, @Param("workerId") String workerId,
        @Param("claimedAt") Instant claimedAt, @Param("claimUntil") Instant claimUntil
    );
}
