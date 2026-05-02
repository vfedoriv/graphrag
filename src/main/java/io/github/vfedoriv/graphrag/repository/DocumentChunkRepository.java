package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface DocumentChunkRepository extends Neo4jRepository<DocumentChunkNode, String> {

    List<DocumentChunkNode> findByDocumentIdOrderByChunkIndexAsc(String documentId);

    void deleteByDocumentId(String documentId);
}
