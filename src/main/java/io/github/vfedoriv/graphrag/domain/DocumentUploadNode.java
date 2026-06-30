package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

@Node("DocumentUpload")
public class DocumentUploadNode {

    @Id
    private String id;
    @Version
    private Long version;
    private String knowledgeBaseId;
    private String originalFilename;
    private String contentType;
    private long sizeBytes;
    private String sha256;
    private String contentUri;
    private DocumentStatus status;
    private Instant uploadedAt;
    private Instant processedAt;
    private String errorMessage;
    private String processingDefaultsJson;
    private Instant processingDefaultsUpdatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getContentUri() {
        return contentUri;
    }

    public void setContentUri(String contentUri) {
        this.contentUri = contentUri;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getProcessingDefaultsJson() {
        return processingDefaultsJson;
    }

    public void setProcessingDefaultsJson(String processingDefaultsJson) {
        this.processingDefaultsJson = processingDefaultsJson;
    }

    public Instant getProcessingDefaultsUpdatedAt() {
        return processingDefaultsUpdatedAt;
    }

    public void setProcessingDefaultsUpdatedAt(Instant processingDefaultsUpdatedAt) {
        this.processingDefaultsUpdatedAt = processingDefaultsUpdatedAt;
    }
}
