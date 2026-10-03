package io.github.vfedoriv.graphrag.documents.adapters.relational.entity;

import io.github.vfedoriv.graphrag.documents.domain.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "document_upload")
@Getter
@Setter
public class DocumentUploadEntity {
    @Id
    private String id;
    @Column(name = "knowledge_base_id", nullable = false)
    private String knowledgeBaseId;
    @Column(name = "original_filename")
    private String originalFilename;
    @Column(name = "content_type")
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "content_uri")
    private String contentUri;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
    @Column(name = "processed_at")
    private Instant processedAt;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "processing_defaults_json")
    private String processingDefaultsJson;
    @Column(name = "processing_defaults_updated_at")
    private Instant processingDefaultsUpdatedAt;
    @Version
    private Long version;
}
