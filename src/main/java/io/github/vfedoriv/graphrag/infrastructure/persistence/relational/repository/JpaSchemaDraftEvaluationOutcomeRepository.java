package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeStatus;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftEvaluationOutcomeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSchemaDraftEvaluationOutcomeRepository
    extends JpaRepository<SchemaDraftEvaluationOutcomeEntity, String> {
    Page<SchemaDraftEvaluationOutcomeEntity> findByRunIdOrderByDocumentIdAsc(String runId, Pageable pageable);
    List<SchemaDraftEvaluationOutcomeEntity> findByRunIdOrderByDocumentIdAsc(String runId);
    Optional<SchemaDraftEvaluationOutcomeEntity>
        findFirstByDraftIdAndReuseKeyAndStatusInOrderByCompletedAtDescIdDesc(
            String draftId, String reuseKey, List<SchemaDraftEvaluationOutcomeStatus> statuses);
}
