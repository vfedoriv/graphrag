package io.github.vfedoriv.graphrag.schemas.evaluation.ports;

import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchemaDraftEvaluationRunRepository {
    Optional<SchemaDraftEvaluationRunNode> findByIdAndDraftId(String id, String draftId);
    List<SchemaDraftEvaluationRunNode> findByStatusIn(List<SchemaDraftEvaluationStatus> statuses);
    List<SchemaDraftEvaluationRunNode> findByDraftIdOrderByCreatedAtDesc(String draftId);
    Optional<SchemaDraftEvaluationRunNode> findFirstByDraftIdAndStatusOrderByCreatedAtDesc(
        String draftId, SchemaDraftEvaluationStatus status);
    Page<SchemaDraftEvaluationRunNode> findPageByDraftId(String draftId, Pageable pageable);
    List<SchemaDraftEvaluationRunNode> findLatestForDraftIds(List<String> draftIds);
    Long claim(String runId, String workerId, Instant claimedAt, Instant claimUntil);
    Long interruptExpired(String runId, String claimedBy, Instant now, Instant completedAt);
    Optional<SchemaDraftEvaluationRunNode> findById(String id);
    SchemaDraftEvaluationRunNode save(SchemaDraftEvaluationRunNode run);
}
