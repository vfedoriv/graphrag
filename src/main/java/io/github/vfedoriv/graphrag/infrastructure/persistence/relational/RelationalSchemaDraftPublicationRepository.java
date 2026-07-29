package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaSchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftPublicationRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDraftPublicationRepository implements SchemaDraftPublicationRepository {
    private final JpaSchemaDraftPublicationRepository repository;

    public RelationalSchemaDraftPublicationRepository(JpaSchemaDraftPublicationRepository repository) {
        this.repository = repository;
    }
    @Override public Optional<SchemaDraftPublicationNode> findByDraftId(String draftId) {
        return repository.findByDraftId(draftId).map(SchemaDraftRelationalMapper::toDomain);
    }
    @Override public Optional<SchemaDraftPublicationNode> findByTargetIdentity(String targetIdentity) {
        return repository.findByTargetIdentity(targetIdentity).map(SchemaDraftRelationalMapper::toDomain);
    }
    @Override public Optional<SchemaDraftPublicationNode> findBySchemaId(String schemaId) {
        return repository.findBySchemaId(schemaId).map(SchemaDraftRelationalMapper::toDomain);
    }
    @Override public Optional<SchemaDraftPublicationNode> findById(String id) {
        return repository.findById(id).map(SchemaDraftRelationalMapper::toDomain);
    }
    @Override public SchemaDraftPublicationNode save(SchemaDraftPublicationNode publication) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(publication)));
    }
}
