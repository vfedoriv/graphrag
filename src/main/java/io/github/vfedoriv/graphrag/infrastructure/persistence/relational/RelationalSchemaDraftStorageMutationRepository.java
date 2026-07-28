package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationState;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaSchemaDraftStorageMutationRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftStorageMutationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Repository
public class RelationalSchemaDraftStorageMutationRepository
    implements SchemaDraftStorageMutationRepository {
    private final JpaSchemaDraftStorageMutationRepository repository;

    public RelationalSchemaDraftStorageMutationRepository(
        JpaSchemaDraftStorageMutationRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public List<SchemaDraftStorageMutationNode> findByStateOrderByCreatedAtAsc(
        SchemaDraftStorageMutationState state
    ) {
        return repository.findByStateOrderByCreatedAtAscIdAsc(state).stream()
            .map(SchemaDraftRelationalMapper::toDomain).toList();
    }

    @Override
    @RelationalTransactional
    public void deleteCompletedBefore(Instant before) {
        repository.deleteByStateAndCompletedAtBefore(SchemaDraftStorageMutationState.COMPLETED, before);
        repository.flush();
    }

    @Override
    public Optional<SchemaDraftStorageMutationNode> findById(String id) {
        return repository.findById(id).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public SchemaDraftStorageMutationNode save(SchemaDraftStorageMutationNode mutation) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(mutation)));
    }
}
