package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftEvaluationRunEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaSchemaDraftEvaluationRunRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftEvaluationRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDraftEvaluationRunRepository implements SchemaDraftEvaluationRunRepository {
    private final JpaSchemaDraftEvaluationRunRepository repository;

    public RelationalSchemaDraftEvaluationRunRepository(JpaSchemaDraftEvaluationRunRepository repository) {
        this.repository = repository;
    }

    @Override public Optional<SchemaDraftEvaluationRunNode> findByIdAndDraftId(String id, String draftId) {
        return repository.findByIdAndDraftId(id, draftId).map(SchemaDraftRelationalMapper::toDomain);
    }
    @Override public List<SchemaDraftEvaluationRunNode> findByStatusIn(List<SchemaDraftEvaluationStatus> statuses) {
        return map(repository.findByStatusIn(statuses));
    }
    @Override public List<SchemaDraftEvaluationRunNode> findByDraftIdOrderByCreatedAtDesc(String draftId) {
        return map(repository.findByDraftIdOrderByCreatedAtDescIdDesc(draftId));
    }
    @Override public Optional<SchemaDraftEvaluationRunNode> findFirstByDraftIdAndStatusOrderByCreatedAtDesc(
        String draftId, SchemaDraftEvaluationStatus status
    ) {
        return repository.findFirstByDraftIdAndStatusOrderByCreatedAtDescIdDesc(draftId, status)
            .map(SchemaDraftRelationalMapper::toDomain);
    }
    @Override public Page<SchemaDraftEvaluationRunNode> findPageByDraftId(String draftId, Pageable pageable) {
        Pageable deterministic = pageable.getSort().isSorted() ? pageable : PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return repository.findByDraftId(draftId, deterministic).map(SchemaDraftRelationalMapper::toDomain);
    }
    @Override public List<SchemaDraftEvaluationRunNode> findLatestForDraftIds(List<String> draftIds) {
        return draftIds.isEmpty() ? List.of() : map(repository.findLatestForDraftIds(draftIds));
    }
    @Override public Long claim(String runId, String workerId, Instant claimedAt, Instant claimUntil) {
        return (long) repository.claim(runId, workerId, claimedAt, claimUntil);
    }
    @Override public Long interruptExpired(String runId, String claimedBy, Instant now, Instant completedAt) {
        return (long) repository.interruptExpired(runId, claimedBy, now, completedAt);
    }
    @Override public Optional<SchemaDraftEvaluationRunNode> findById(String id) {
        return repository.findById(id).map(SchemaDraftRelationalMapper::toDomain);
    }
    @Override public SchemaDraftEvaluationRunNode save(SchemaDraftEvaluationRunNode run) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(run)));
    }
    private List<SchemaDraftEvaluationRunNode> map(List<SchemaDraftEvaluationRunEntity> values) {
        return values.stream().map(SchemaDraftRelationalMapper::toDomain).toList();
    }
}
