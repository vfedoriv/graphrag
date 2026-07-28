package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
public class DocumentStorageMutationNode {

    private String id;
    private Long version;
    private DocumentStorageMutationType type;
    private DocumentStorageMutationState state;
    private String knowledgeBaseId;
    private String documentId;
    private String contentUri;
    private String previousContentUri;
    private int retryCount;
    private String lastError;
    private Instant createdAt;
    private Instant completedAt;
    private Instant updatedAt;
    private String claimedBy;
    private Instant claimUntil;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public DocumentStorageMutationType getType() { return type; }
    public void setType(DocumentStorageMutationType type) { this.type = type; }
    public DocumentStorageMutationState getState() { return state; }
    public void setState(DocumentStorageMutationState state) { this.state = state; }
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getContentUri() { return contentUri; }
    public void setContentUri(String contentUri) { this.contentUri = contentUri; }
    public String getPreviousContentUri() { return previousContentUri; }
    public void setPreviousContentUri(String previousContentUri) { this.previousContentUri = previousContentUri; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
    public Instant getClaimUntil() { return claimUntil; }
    public void setClaimUntil(Instant claimUntil) { this.claimUntil = claimUntil; }
}
