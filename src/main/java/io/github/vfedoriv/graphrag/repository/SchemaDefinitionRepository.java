package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface SchemaDefinitionRepository extends Neo4jRepository<SchemaDefinitionNode, String> {
    boolean existsByNameAndVersion(String name, int version);

    @Query("""
        MATCH (:KnowledgeBase {id: $knowledgeBaseId})-[:USES_SCHEMA]->(s:SchemaDefinition)
        RETURN s
        """)
    List<SchemaDefinitionNode> findAllByKnowledgeBaseId(String knowledgeBaseId);

    @Query("""
        MATCH (kb:KnowledgeBase {id: $knowledgeBaseId})
        MATCH (s:SchemaDefinition {id: $schemaId})
        MERGE (kb)-[:USES_SCHEMA]->(s)
        """)
    void associateWithKnowledgeBase(String knowledgeBaseId, String schemaId);

    @Query("""
        MATCH (kb:KnowledgeBase)
        WHERE kb.activeSchemaId = $schemaId
        RETURN count(kb) > 0
        """)
    boolean existsActiveKnowledgeBaseReference(String schemaId);

    @Query("""
        MATCH (:KnowledgeBase)-[r:USES_SCHEMA]->(:SchemaDefinition {id: $schemaId})
        DELETE r
        """)
    void detachKnowledgeBaseAssociations(String schemaId);
}
