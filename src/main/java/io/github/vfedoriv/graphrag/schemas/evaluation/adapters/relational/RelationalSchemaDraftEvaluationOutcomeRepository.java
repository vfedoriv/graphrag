package io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational;

import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationOutcomeNode;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationOutcomeStatus;
import io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational.entity.SchemaDraftEvaluationOutcomeEntity;
import io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational.repository.JpaSchemaDraftEvaluationOutcomeRepository;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.SchemaDraftEvaluationOutcomeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDraftEvaluationOutcomeRepository implements SchemaDraftEvaluationOutcomeRepository {
    private final JpaSchemaDraftEvaluationOutcomeRepository repository;

    public RelationalSchemaDraftEvaluationOutcomeRepository(JpaSchemaDraftEvaluationOutcomeRepository repository) {
        this.repository = repository;
    }
    @Override public Page<SchemaDraftEvaluationOutcomeNode> findByRunIdOrderByDocumentIdAsc(
        String runId, Pageable pageable
    ) {
        return repository.findByRunIdOrderByDocumentIdAsc(runId, pageable).map(EvaluationRelationalMapper::toDomain);
    }
    @Override public List<SchemaDraftEvaluationOutcomeNode> findByRunIdOrderByDocumentIdAsc(String runId) {
        return map(repository.findByRunIdOrderByDocumentIdAsc(runId));
    }
    @Override public Optional<SchemaDraftEvaluationOutcomeNode>
        findFirstByDraftIdAndReuseKeyAndStatusInOrderByCompletedAtDesc(
            String draftId, String reuseKey, List<SchemaDraftEvaluationOutcomeStatus> statuses
        ) {
        return repository.findFirstByDraftIdAndReuseKeyAndStatusInOrderByCompletedAtDescIdDesc(
            draftId, reuseKey, statuses).map(EvaluationRelationalMapper::toDomain);
    }
    @Override public Optional<SchemaDraftEvaluationOutcomeNode> findById(String id) {
        return repository.findById(id).map(EvaluationRelationalMapper::toDomain);
    }
    @Override public SchemaDraftEvaluationOutcomeNode save(SchemaDraftEvaluationOutcomeNode outcome) {
        return EvaluationRelationalMapper.toDomain(
            repository.saveAndFlush(EvaluationRelationalMapper.toEntity(outcome)));
    }
    private List<SchemaDraftEvaluationOutcomeNode> map(List<SchemaDraftEvaluationOutcomeEntity> values) {
        return values.stream().map(EvaluationRelationalMapper::toDomain).toList();
    }
}
