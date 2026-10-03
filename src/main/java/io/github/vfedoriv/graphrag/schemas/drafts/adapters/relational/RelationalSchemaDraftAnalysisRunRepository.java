package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational;

import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftAnalysisRunEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.repository.JpaSchemaDraftAnalysisRunRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftAnalysisRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDraftAnalysisRunRepository implements SchemaDraftAnalysisRunRepository {
    private final JpaSchemaDraftAnalysisRunRepository repository;

    public RelationalSchemaDraftAnalysisRunRepository(JpaSchemaDraftAnalysisRunRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SchemaDraftAnalysisRunNode> findFirstByDraftIdAndStatusOrderByCreatedAtDesc(
        String draftId, SchemaDraftAnalysisStatus status
    ) {
        return repository.findFirstByDraftIdAndStatusOrderByCreatedAtDescIdDesc(draftId, status)
            .map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public List<SchemaDraftAnalysisRunNode> findByStatus(SchemaDraftAnalysisStatus status) {
        return map(repository.findByStatus(status));
    }

    @Override
    public List<SchemaDraftAnalysisRunNode> findByDraftIdOrderByCreatedAtDesc(String draftId) {
        return map(repository.findByDraftIdOrderByCreatedAtDescIdDesc(draftId));
    }

    @Override
    public Optional<SchemaDraftAnalysisRunNode> findByIdAndDraftId(String id, String draftId) {
        return repository.findByIdAndDraftId(id, draftId).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public Page<SchemaDraftAnalysisRunNode> findPageByDraftId(String draftId, Pageable pageable) {
        Pageable deterministic = pageable.getSort().isSorted() ? pageable : PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return repository.findByDraftId(draftId, deterministic).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public List<SchemaDraftAnalysisRunNode> findCurrentForDraftIds(List<String> draftIds) {
        if (draftIds.isEmpty()) {
            return List.of();
        }
        return map(repository.findCurrentForDraftIds(draftIds));
    }

    @Override
    public Long claim(String runId, String workerId, Instant claimedAt) {
        return (long) repository.claim(runId, workerId, claimedAt);
    }

    @Override
    public Optional<SchemaDraftAnalysisRunNode> findById(String id) {
        return repository.findById(id).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public SchemaDraftAnalysisRunNode save(SchemaDraftAnalysisRunNode run) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(run)));
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
        repository.flush();
    }

    private List<SchemaDraftAnalysisRunNode> map(
        List<io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftAnalysisRunEntity> values
    ) {
        return values.stream().map(SchemaDraftRelationalMapper::toDomain).toList();
    }
}
