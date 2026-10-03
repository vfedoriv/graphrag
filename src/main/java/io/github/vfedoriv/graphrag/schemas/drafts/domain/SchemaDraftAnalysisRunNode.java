package io.github.vfedoriv.graphrag.schemas.drafts.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaDraftAnalysisRunNode {
    private String id;
    private Long persistenceVersion;
    private String draftId;
    private String knowledgeBaseId;
    private SchemaDraftAnalysisStatus status;
    private long draftRevision;
    private long guidanceRevision;
    private String guidanceFingerprint;
    private String sourceSnapshotJson;
    private String sourceMembershipFingerprint;
    private String aiProfileId;
    private long aiProfileRevision;
    private int configuredTimeoutSeconds;
    private int configuredSdkMaxRetries;
    private String promptRevision;
    private String candidateRevision;
    private String settingsFingerprint;
    private Integer discoveryMaxConcurrency;
    private Long discoverySourceTimeoutMillis;
    private Long discoveryRequestTimeoutMillis;
    private String snapshotFingerprint;
    private String retryOfRunId;
    private String claimedBy;
    private Instant claimedAt;
    private int totalSources;
    private int succeededSources;
    private int failedSources;
    private boolean currentResult;
    private String aggregateRevisionId;
    private String failureCategory;
    private boolean retryable;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
}
