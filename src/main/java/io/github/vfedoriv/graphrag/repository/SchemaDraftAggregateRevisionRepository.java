package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftAggregateRevisionNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SchemaDraftAggregateRevisionRepository extends Neo4jRepository<SchemaDraftAggregateRevisionNode, String> {
    List<SchemaDraftAggregateRevisionNode> findByDraftIdOrderByRevisionDesc(String draftId);
    Optional<SchemaDraftAggregateRevisionNode> findFirstByDraftIdOrderByRevisionDesc(String draftId);
}
