package io.github.vfedoriv.graphrag.documents.domain;

import java.time.Instant;
public class ExtractionRunNode {

    private String id;
    private Long version;
    private String documentId;
    private String schemaId;
    private String model;
    private ExtractionRunStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
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

    public String getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public ExtractionRunStatus getStatus() {
        return status;
    }

    public void setStatus(ExtractionRunStatus status) {
        this.status = status;
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

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public String getRetryOfRunId() { return retryOfRunId; }
    public void setRetryOfRunId(String retryOfRunId) { this.retryOfRunId = retryOfRunId; }
    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
    public Instant getClaimUntil() { return claimUntil; }
    public void setClaimUntil(Instant claimUntil) { this.claimUntil = claimUntil; }
}
