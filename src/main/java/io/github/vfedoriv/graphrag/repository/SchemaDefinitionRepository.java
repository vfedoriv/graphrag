package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SchemaDefinitionRepository extends Neo4jRepository<SchemaDefinitionNode, String> {
    Optional<SchemaDefinitionNode> findByNameAndVersion(String name, int version);
}
