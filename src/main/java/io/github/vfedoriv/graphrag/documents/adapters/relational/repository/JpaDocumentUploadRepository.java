package io.github.vfedoriv.graphrag.documents.adapters.relational.repository;

import io.github.vfedoriv.graphrag.documents.adapters.relational.entity.DocumentUploadEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaDocumentUploadRepository extends JpaRepository<DocumentUploadEntity, String> {
    Optional<DocumentUploadEntity> findByKnowledgeBaseIdAndSha256(String knowledgeBaseId, String sha256);
    List<DocumentUploadEntity> findByKnowledgeBaseIdOrderByUploadedAtDescIdDesc(String knowledgeBaseId);
    Optional<DocumentUploadEntity> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);
    List<DocumentUploadEntity> findAllByIdInAndKnowledgeBaseId(List<String> ids, String knowledgeBaseId);
    @Query("""
        SELECT document FROM DocumentUploadEntity document
        WHERE document.knowledgeBaseId = :knowledgeBaseId
          AND (:originalFilename IS NULL OR LOWER(document.originalFilename) = LOWER(:originalFilename))
          AND (:contentType IS NULL OR LOWER(document.contentType) = LOWER(:contentType))
        ORDER BY document.uploadedAt DESC, document.id DESC
        """)
    Page<DocumentUploadEntity> findByMetadata(
        @Param("knowledgeBaseId") String knowledgeBaseId,
        @Param("originalFilename") String originalFilename,
        @Param("contentType") String contentType,
        Pageable pageable
    );
    long countByKnowledgeBaseId(String knowledgeBaseId);
    Page<DocumentUploadEntity> findByKnowledgeBaseId(String knowledgeBaseId, Pageable pageable);
}
