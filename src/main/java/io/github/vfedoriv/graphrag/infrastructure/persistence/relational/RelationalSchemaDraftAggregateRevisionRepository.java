package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaDraftAggregateRevisionNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaSchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDraftAggregateRevisionRepository
    implements SchemaDraftAggregateRevisionRepository {
    private final JpaSchemaDraftAggregateRevisionRepository repository;

    public RelationalSchemaDraftAggregateRevisionRepository(
        JpaSchemaDraftAggregateRevisionRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public List<SchemaDraftAggregateRevisionNode> findByDraftIdOrderByRevisionDesc(String draftId) {
        return repository.findByDraftIdOrderByRevisionDesc(draftId).stream()
            .map(SchemaDraftRelationalMapper::toDomain).toList();
    }

    @Override
    public Optional<SchemaDraftAggregateRevisionNode> findFirstByDraftIdOrderByRevisionDesc(String draftId) {
        return repository.findFirstByDraftIdOrderByRevisionDesc(draftId)
            .map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public Optional<SchemaDraftAggregateRevisionNode> findLegacyPreviousPromoted(
        String draftId, String aggregateId
    ) {
        return repository.findLegacyPreviousPromoted(draftId, aggregateId)
            .map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public Optional<SchemaDraftAggregateRevisionNode> findById(String id) {
        return repository.findById(id).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public SchemaDraftAggregateRevisionNode save(SchemaDraftAggregateRevisionNode aggregate) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(aggregate)));
    }
}
