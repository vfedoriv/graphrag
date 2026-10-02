package io.github.vfedoriv.graphrag.schemas.publication.adapters.relational.repository;

import io.github.vfedoriv.graphrag.schemas.publication.adapters.relational.entity.SchemaDraftPublicationEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSchemaDraftPublicationRepository extends JpaRepository<SchemaDraftPublicationEntity, String> {
    Optional<SchemaDraftPublicationEntity> findByDraftId(String draftId);
    Optional<SchemaDraftPublicationEntity> findByTargetIdentity(String targetIdentity);
    Optional<SchemaDraftPublicationEntity> findBySchemaId(String schemaId);
}
