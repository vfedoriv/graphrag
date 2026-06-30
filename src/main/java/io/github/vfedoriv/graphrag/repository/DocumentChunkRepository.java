package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface DocumentChunkRepository extends Neo4jRepository<DocumentChunkNode, String> {

    List<DocumentChunkNode> findByDocumentIdOrderByChunkIndexAsc(String documentId);

    Long deleteByDocumentId(String documentId);

    @Query("""
        MATCH (:DocumentUpload {knowledgeBaseId: $knowledgeBaseId})-[:HAS_CHUNK]->(chunk:DocumentChunk)
        WHERE chunk.embedding IS NOT NULL
        RETURN chunk
        LIMIT 1
        """)
    List<DocumentChunkNode> findFirstEmbeddedChunkByKnowledgeBaseId(String knowledgeBaseId);
}
