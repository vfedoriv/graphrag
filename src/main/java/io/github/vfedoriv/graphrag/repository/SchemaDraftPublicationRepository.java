package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationNode;
import java.util.Optional;

public interface SchemaDraftPublicationRepository {
    Optional<SchemaDraftPublicationNode> findByDraftId(String draftId);
    Optional<SchemaDraftPublicationNode> findByTargetIdentity(String targetIdentity);
    Optional<SchemaDraftPublicationNode> findBySchemaId(String schemaId);
    Optional<SchemaDraftPublicationNode> findById(String id);
    SchemaDraftPublicationNode save(SchemaDraftPublicationNode publication);
}
