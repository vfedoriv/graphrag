package io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational.repository;

import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingItemStatus;
import io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational.entity.SchemaReprocessingItemEntity;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSchemaReprocessingItemRepository extends JpaRepository<SchemaReprocessingItemEntity, String> {
    Page<SchemaReprocessingItemEntity> findByPlanIdOrderByDocumentIdAsc(String planId, Pageable pageable);
    List<SchemaReprocessingItemEntity> findByPlanIdOrderByDocumentIdAsc(String planId);

    @Query("""
        select item from SchemaReprocessingItemEntity item
        where item.status = io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingItemStatus.RUNNING
          and item.claimUntil < :now
        order by item.claimUntil asc, item.id asc
        """)
    List<SchemaReprocessingItemEntity> findExpiredClaims(@Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query("""
        update SchemaReprocessingItemEntity item
        set item.status = io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingItemStatus.RUNNING,
            item.claimedBy = :workerId, item.claimedAt = :claimedAt, item.claimUntil = :claimUntil,
            item.startedAt = :claimedAt, item.persistenceVersion = item.persistenceVersion + 1
        where item.id = :itemId
          and item.status = io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingItemStatus.QUEUED
          and item.retryable = true and item.claimedBy is null
        """)
    int claim(
        @Param("itemId") String itemId, @Param("workerId") String workerId,
        @Param("claimedAt") Instant claimedAt, @Param("claimUntil") Instant claimUntil
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @RelationalTransactional
    @Query("""
        update SchemaReprocessingItemEntity item
        set item.status = :status, item.failureCategory = :failureCategory,
            item.retryable = :retryable, item.completedAt = :completedAt,
            item.persistenceVersion = item.persistenceVersion + 1
        where item.id = :itemId
          and item.status = io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingItemStatus.RUNNING
          and item.claimedBy = :workerId
        """)
    int complete(
        @Param("itemId") String itemId, @Param("workerId") String workerId,
        @Param("status") SchemaReprocessingItemStatus status,
        @Param("failureCategory") String failureCategory, @Param("retryable") boolean retryable,
        @Param("completedAt") Instant completedAt
    );
}
