package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational;

import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftConflictEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftConflictNode;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.repository.JpaSchemaDraftConflictRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftConflictRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDraftConflictRepository implements SchemaDraftConflictRepository {
    private final JpaSchemaDraftConflictRepository repository;

    public RelationalSchemaDraftConflictRepository(JpaSchemaDraftConflictRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SchemaDraftConflictNode> findByIdAndDraftId(String id, String draftId) {
        return repository.findByIdAndDraftId(id, draftId).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public List<SchemaDraftConflictNode> findByAggregateRevision(String draftId, String aggregateRevisionId) {
        return map(repository.findByDraftIdAndAggregateRevisionIdOrderByCoordinateAscIdAsc(
            draftId, aggregateRevisionId));
    }

    @Override
    public List<SchemaDraftConflictNode> findHistory(String draftId) {
        return map(repository.findHistory(draftId));
    }

    @Override
    public List<SchemaDraftConflictNode> findResolvedHistory(String draftId, String aggregateRevisionId) {
        return map(repository.findResolvedHistory(draftId, aggregateRevisionId));
    }

    @Override
    public SchemaDraftConflictNode save(SchemaDraftConflictNode conflict) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(conflict)));
    }

    private List<SchemaDraftConflictNode> map(
        List<io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftConflictEntity> values
    ) {
        return values.stream().map(SchemaDraftRelationalMapper::toDomain).toList();
    }
}
