package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultStatus;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaSchemaDraftSourceResultRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceResultRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDraftSourceResultRepository implements SchemaDraftSourceResultRepository {
    private final JpaSchemaDraftSourceResultRepository repository;

    public RelationalSchemaDraftSourceResultRepository(JpaSchemaDraftSourceResultRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SchemaDraftSourceResultNode> findFirstByDraftIdAndReuseKeyAndStatusOrderByCompletedAtDesc(
        String draftId, String reuseKey, SchemaDraftSourceResultStatus status
    ) {
        return repository.findFirstByDraftIdAndReuseKeyAndStatusOrderByCompletedAtDescIdDesc(
            draftId, reuseKey, status).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public List<SchemaDraftSourceResultNode> findByRunIdOrderByCreatedAtAsc(String runId) {
        return repository.findByRunIdOrderByCreatedAtAscIdAsc(runId).stream()
            .map(SchemaDraftRelationalMapper::toDomain).toList();
    }

    @Override
    public Page<SchemaDraftSourceResultNode> findPageByRunId(String runId, Pageable pageable) {
        Pageable deterministic = pageable.getSort().isSorted() ? pageable : PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        return repository.findByRunId(runId, deterministic).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public List<String> findContributingSourceSha256s(String draftId) {
        return repository.findContributingSourceSha256s(draftId);
    }

    @Override
    public List<String> findHistoricalContributingDocumentIds(String draftId) {
        return repository.findHistoricalContributingDocumentIds(draftId);
    }

    @Override
    public SchemaDraftSourceResultNode save(SchemaDraftSourceResultNode result) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(result)));
    }
}
