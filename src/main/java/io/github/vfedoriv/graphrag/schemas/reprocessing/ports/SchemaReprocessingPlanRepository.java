package io.github.vfedoriv.graphrag.schemas.reprocessing.ports;

import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ChunkReprocessingSelection;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ReprocessingPlanReason;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchemaReprocessingPlanRepository {
    Optional<SchemaReprocessingPlanNode> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);
    boolean existsActiveByKnowledgeBaseId(String knowledgeBaseId);
    List<SchemaReprocessingPlanNode> findByStatusIn(List<SchemaReprocessingPlanStatus> statuses);
    Page<SchemaReprocessingPlanNode> findPageByKnowledgeBaseId(String knowledgeBaseId, Pageable pageable);
    Page<SchemaReprocessingPlanNode> findPageByKnowledgeBaseIdAndDraftId(
        String knowledgeBaseId, String draftId, Pageable pageable);
    Page<SchemaReprocessingPlanNode> findPageByFilters(
        String knowledgeBaseId, String draftId, ReprocessingPlanReason reason,
        ChunkReprocessingSelection selection, SchemaReprocessingPlanStatus status, Pageable pageable);
    List<SchemaReprocessingPlanNode> findLatestForDraftIds(List<String> draftIds);
    Long claim(String planId, String workerId, Instant claimedAt, Instant claimUntil);
    Optional<SchemaReprocessingPlanNode> findById(String id);
    SchemaReprocessingPlanNode save(SchemaReprocessingPlanNode plan);
}
