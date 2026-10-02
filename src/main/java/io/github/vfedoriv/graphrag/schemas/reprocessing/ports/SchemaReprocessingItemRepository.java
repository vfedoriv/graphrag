package io.github.vfedoriv.graphrag.schemas.reprocessing.ports;

import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingItemStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchemaReprocessingItemRepository {
    Page<SchemaReprocessingItemNode> findByPlanIdOrderByDocumentIdAsc(String planId, Pageable pageable);
    List<SchemaReprocessingItemNode> findByPlanIdOrderByDocumentIdAsc(String planId);
    List<SchemaReprocessingItemNode> findExpiredClaims(Instant now);
    Long claim(String itemId, String workerId, Instant claimedAt, Instant claimUntil);
    Long complete(
        String itemId, String workerId, SchemaReprocessingItemStatus status,
        String failureCategory, boolean retryable, Instant completedAt
    );
    Optional<SchemaReprocessingItemNode> findById(String id);
    SchemaReprocessingItemNode save(SchemaReprocessingItemNode item);
}
