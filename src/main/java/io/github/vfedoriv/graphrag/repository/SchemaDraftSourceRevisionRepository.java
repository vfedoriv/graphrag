package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceRevisionNode;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SchemaDraftSourceRevisionRepository extends Neo4jRepository<SchemaDraftSourceRevisionNode, String> {
    List<SchemaDraftSourceRevisionNode> findBySourceIdOrderByRevisionDesc(String sourceId);
}
