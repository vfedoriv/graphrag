package io.github.vfedoriv.graphrag.schemas.publication.adapters.relational;

import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.schemas.publication.adapters.relational.repository.JpaSchemaDraftPublicationRepository;
import io.github.vfedoriv.graphrag.schemas.publication.ports.SchemaDraftPublicationRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDraftPublicationRepository implements SchemaDraftPublicationRepository {
    private final JpaSchemaDraftPublicationRepository repository;

    public RelationalSchemaDraftPublicationRepository(JpaSchemaDraftPublicationRepository repository) {
        this.repository = repository;
    }
    @Override public Optional<SchemaDraftPublicationNode> findByDraftId(String draftId) {
        return repository.findByDraftId(draftId).map(PublicationRelationalMapper::toDomain);
    }
    @Override public Optional<SchemaDraftPublicationNode> findByTargetIdentity(String targetIdentity) {
        return repository.findByTargetIdentity(targetIdentity).map(PublicationRelationalMapper::toDomain);
    }
    @Override public Optional<SchemaDraftPublicationNode> findBySchemaId(String schemaId) {
        return repository.findBySchemaId(schemaId).map(PublicationRelationalMapper::toDomain);
    }
    @Override public Optional<SchemaDraftPublicationNode> findById(String id) {
        return repository.findById(id).map(PublicationRelationalMapper::toDomain);
    }
    @Override public SchemaDraftPublicationNode save(SchemaDraftPublicationNode publication) {
        return PublicationRelationalMapper.toDomain(
            repository.saveAndFlush(PublicationRelationalMapper.toEntity(publication)));
    }
}
