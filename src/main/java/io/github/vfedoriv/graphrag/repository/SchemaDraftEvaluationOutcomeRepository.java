package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchemaDraftEvaluationOutcomeRepository {
    Page<SchemaDraftEvaluationOutcomeNode> findByRunIdOrderByDocumentIdAsc(String runId, Pageable pageable);
    List<SchemaDraftEvaluationOutcomeNode> findByRunIdOrderByDocumentIdAsc(String runId);
    Optional<SchemaDraftEvaluationOutcomeNode> findFirstByDraftIdAndReuseKeyAndStatusInOrderByCompletedAtDesc(
        String draftId, String reuseKey, List<SchemaDraftEvaluationOutcomeStatus> statuses);
    Optional<SchemaDraftEvaluationOutcomeNode> findById(String id);
    SchemaDraftEvaluationOutcomeNode save(SchemaDraftEvaluationOutcomeNode outcome);
}
