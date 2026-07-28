package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceRevisionNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaSchemaDraftSourceRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftSourceRevisionRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDraftSourceRevisionRepository implements SchemaDraftSourceRevisionRepository {
    private final JpaSchemaDraftSourceRevisionRepository repository;

    public RelationalSchemaDraftSourceRevisionRepository(JpaSchemaDraftSourceRevisionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SchemaDraftSourceRevisionNode> findBySourceIdOrderByRevisionDesc(String sourceId) {
        return repository.findBySourceIdOrderByRevisionDesc(sourceId).stream()
            .map(SchemaDraftRelationalMapper::toDomain).toList();
    }

    @Override
    public SchemaDraftSourceRevisionNode save(SchemaDraftSourceRevisionNode revision) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(revision)));
    }
}
