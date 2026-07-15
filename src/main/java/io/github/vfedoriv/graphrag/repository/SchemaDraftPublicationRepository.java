package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationNode;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SchemaDraftPublicationRepository extends Neo4jRepository<SchemaDraftPublicationNode, String> {
    Optional<SchemaDraftPublicationNode> findByDraftId(String draftId);
    Optional<SchemaDraftPublicationNode> findByTargetIdentity(String targetIdentity);
}
