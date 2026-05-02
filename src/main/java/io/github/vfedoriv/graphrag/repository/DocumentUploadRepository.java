package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface DocumentUploadRepository extends Neo4jRepository<DocumentUploadNode, String> {

    Optional<DocumentUploadNode> findByKnowledgeBaseIdAndSha256(String knowledgeBaseId, String sha256);
}
