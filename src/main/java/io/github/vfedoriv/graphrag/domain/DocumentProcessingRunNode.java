package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
public class DocumentProcessingRunNode {

    private String id;
    private Long version;
    private String documentId;
    private String knowledgeBaseId;
    private String sourceSha256;
    private String parserId;
    private String fileFormat;
    private String requestedOptionsJson;
    private String savedDefaultsJson;
    private String effectiveOptionsJson;
    private DocumentProcessingRunStatus status;
    private String stage;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
    private boolean activeCompleted;
    private int retryCount;
    private String retryOfRunId;
    private String claimedBy;
    private Instant claimUntil;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getSourceSha256() {
        return sourceSha256;
    }

    public void setSourceSha256(String sourceSha256) {
        this.sourceSha256 = sourceSha256;
    }

    public String getParserId() {
        return parserId;
    }

    public void setParserId(String parserId) {
        this.parserId = parserId;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
    }

    public String getRequestedOptionsJson() {
        return requestedOptionsJson;
    }

    public void setRequestedOptionsJson(String requestedOptionsJson) {
        this.requestedOptionsJson = requestedOptionsJson;
    }

    public String getSavedDefaultsJson() {
        return savedDefaultsJson;
    }

    public void setSavedDefaultsJson(String savedDefaultsJson) {
        this.savedDefaultsJson = savedDefaultsJson;
    }

    public String getEffectiveOptionsJson() {
        return effectiveOptionsJson;
    }

    public void setEffectiveOptionsJson(String effectiveOptionsJson) {
        this.effectiveOptionsJson = effectiveOptionsJson;
    }

    public DocumentProcessingRunStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentProcessingRunStatus status) {
        this.status = status;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isActiveCompleted() {
        return activeCompleted;
    }

    public void setActiveCompleted(boolean activeCompleted) {
        this.activeCompleted = activeCompleted;
    }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public String getRetryOfRunId() { return retryOfRunId; }
    public void setRetryOfRunId(String retryOfRunId) { this.retryOfRunId = retryOfRunId; }
    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
    public Instant getClaimUntil() { return claimUntil; }
    public void setClaimUntil(Instant claimUntil) { this.claimUntil = claimUntil; }
}
