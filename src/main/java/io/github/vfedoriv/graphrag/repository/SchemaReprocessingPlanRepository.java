package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchemaReprocessingPlanRepository {
    Optional<SchemaReprocessingPlanNode> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);
    List<SchemaReprocessingPlanNode> findByStatusIn(List<SchemaReprocessingPlanStatus> statuses);
    Page<SchemaReprocessingPlanNode> findPageByKnowledgeBaseId(String knowledgeBaseId, Pageable pageable);
    Page<SchemaReprocessingPlanNode> findPageByKnowledgeBaseIdAndDraftId(
        String knowledgeBaseId, String draftId, Pageable pageable);
    List<SchemaReprocessingPlanNode> findLatestForDraftIds(List<String> draftIds);
    Long claim(String planId, String workerId, Instant claimedAt, Instant claimUntil);
    Optional<SchemaReprocessingPlanNode> findById(String id);
    SchemaReprocessingPlanNode save(SchemaReprocessingPlanNode plan);
}
