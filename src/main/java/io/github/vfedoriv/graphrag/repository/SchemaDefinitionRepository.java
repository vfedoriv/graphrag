package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import java.util.Optional;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface SchemaDefinitionRepository extends Neo4jRepository<SchemaDefinitionNode, String> {
    Optional<SchemaDefinitionNode> findByNameAndVersion(String name, int version);

    @Query("""
        MATCH (:KnowledgeBase {id: $knowledgeBaseId})-[:USES_SCHEMA]->(s:SchemaDefinition)
        RETURN s
        """)
    List<SchemaDefinitionNode> findAllByKnowledgeBaseId(String knowledgeBaseId);
}
