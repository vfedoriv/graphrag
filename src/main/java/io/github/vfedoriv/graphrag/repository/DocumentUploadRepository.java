package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DocumentUploadRepository extends Neo4jRepository<DocumentUploadNode, String> {

    Optional<DocumentUploadNode> findByKnowledgeBaseIdAndSha256(String knowledgeBaseId, String sha256);

    List<DocumentUploadNode> findByKnowledgeBaseIdOrderByUploadedAtDesc(String knowledgeBaseId);

    Optional<DocumentUploadNode> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);

    long countByKnowledgeBaseId(String knowledgeBaseId);

    @Query(value = """
        MATCH (document:DocumentUpload {knowledgeBaseId: $knowledgeBaseId})
        RETURN document ORDER BY document.uploadedAt DESC, document.id DESC SKIP $skip LIMIT $limit
        """, countQuery = """
        MATCH (document:DocumentUpload {knowledgeBaseId: $knowledgeBaseId}) RETURN count(document)
        """)
    Page<DocumentUploadNode> findPageByKnowledgeBaseId(String knowledgeBaseId, Pageable pageable);
}
