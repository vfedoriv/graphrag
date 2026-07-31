package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DocumentUploadRepository {
    Optional<DocumentUploadNode> findByKnowledgeBaseIdAndSha256(String knowledgeBaseId, String sha256);
    List<DocumentUploadNode> findByKnowledgeBaseIdOrderByUploadedAtDesc(String knowledgeBaseId);
    Optional<DocumentUploadNode> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);
    List<DocumentUploadNode> findAllByIdInAndKnowledgeBaseId(List<String> ids, String knowledgeBaseId);
    List<DocumentUploadNode> findByMetadata(
        String knowledgeBaseId,
        String originalFilename,
        String contentType,
        int limit
    );
    long countByKnowledgeBaseId(String knowledgeBaseId);
    Page<DocumentUploadNode> findPageByKnowledgeBaseId(String knowledgeBaseId, Pageable pageable);
    List<DocumentUploadNode> findAll();
    Optional<DocumentUploadNode> findById(String id);
    boolean existsById(String id);
    DocumentUploadNode save(DocumentUploadNode document);
    void delete(DocumentUploadNode document);
}
