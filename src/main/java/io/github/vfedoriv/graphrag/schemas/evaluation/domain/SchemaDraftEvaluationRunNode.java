package io.github.vfedoriv.graphrag.schemas.evaluation.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftEvaluationRunNode {
    private String id;
    private Long persistenceVersion;
    private String draftId;
    private String knowledgeBaseId;
    private SchemaDraftEvaluationStatus status;
    private long draftRevision;
    private String aggregateRevisionId;
    private String projectionJson;
    private String projectionContentHash;
    private String guidanceJson;
    private String decisionsJson;
    private String decisionsFingerprint;
    private String documentSnapshotJson;
    private String aiProfileId;
    private long aiProfileRevision;
    private String promptRevision;
    private String contractRevision;
    private String settingsJson;
    private String snapshotFingerprint;
    private String retryOfRunId;
    private int retryCount;
    private String claimedBy;
    private Instant claimedAt;
    private Instant claimUntil;
    private int totalDocuments;
    private int succeededDocuments;
    private int failedDocuments;
    private int staleDocuments;
    private String metricsJson;
    private String advisoryJson;
    private String failureCategory;
    private boolean retryable;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
}
