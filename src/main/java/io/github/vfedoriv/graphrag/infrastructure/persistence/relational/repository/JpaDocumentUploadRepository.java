package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.DocumentUploadEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDocumentUploadRepository extends JpaRepository<DocumentUploadEntity, String> {
    Optional<DocumentUploadEntity> findByKnowledgeBaseIdAndSha256(String knowledgeBaseId, String sha256);
    List<DocumentUploadEntity> findByKnowledgeBaseIdOrderByUploadedAtDescIdDesc(String knowledgeBaseId);
    Optional<DocumentUploadEntity> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);
    long countByKnowledgeBaseId(String knowledgeBaseId);
    Page<DocumentUploadEntity> findByKnowledgeBaseId(String knowledgeBaseId, Pageable pageable);
}
