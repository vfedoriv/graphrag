package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SchemaDraftConflictRepository extends Neo4jRepository<SchemaDraftConflictNode, String> {
    List<SchemaDraftConflictNode> findByDraftIdOrderByCoordinateAsc(String draftId);
    Optional<SchemaDraftConflictNode> findByIdAndDraftId(String id, String draftId);
}
