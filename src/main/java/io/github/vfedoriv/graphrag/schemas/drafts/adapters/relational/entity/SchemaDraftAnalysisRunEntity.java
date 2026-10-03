package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisStatus;
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
@Table(name = "schema_draft_analysis_run")
@Getter
@Setter
public class SchemaDraftAnalysisRunEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "draft_id", nullable = false) private String draftId;
    @Column(name = "knowledge_base_id", nullable = false) private String knowledgeBaseId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftAnalysisStatus status;
    @Column(name = "draft_revision", nullable = false) private long draftRevision;
    @Column(name = "guidance_revision", nullable = false) private long guidanceRevision;
    @Column(name = "guidance_fingerprint", nullable = false, length = 64) private String guidanceFingerprint;
    @Column(name = "source_snapshot_json", nullable = false) private String sourceSnapshotJson;
    @Column(name = "source_membership_fingerprint", nullable = false, length = 64)
    private String sourceMembershipFingerprint;
    @Column(name = "ai_profile_id", nullable = false) private String aiProfileId;
    @Column(name = "ai_profile_revision", nullable = false) private long aiProfileRevision;
    @Column(name = "configured_timeout_seconds", nullable = false) private int configuredTimeoutSeconds;
    @Column(name = "configured_sdk_max_retries", nullable = false) private int configuredSdkMaxRetries;
    @Column(name = "prompt_revision", nullable = false) private String promptRevision;
    @Column(name = "candidate_revision", nullable = false) private String candidateRevision;
    @Column(name = "settings_fingerprint", length = 64) private String settingsFingerprint;
    @Column(name = "discovery_max_concurrency") private Integer discoveryMaxConcurrency;
    @Column(name = "discovery_source_timeout_millis") private Long discoverySourceTimeoutMillis;
    @Column(name = "discovery_request_timeout_millis") private Long discoveryRequestTimeoutMillis;
    @Column(name = "snapshot_fingerprint", nullable = false, length = 64) private String snapshotFingerprint;
    @Column(name = "retry_of_run_id") private String retryOfRunId;
    @Column(name = "claimed_by") private String claimedBy;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "total_sources", nullable = false) private int totalSources;
    @Column(name = "succeeded_sources", nullable = false) private int succeededSources;
    @Column(name = "failed_sources", nullable = false) private int failedSources;
    @Column(name = "current_result", nullable = false) private boolean currentResult;
    @Column(name = "aggregate_revision_id") private String aggregateRevisionId;
    @Column(name = "failure_category") private String failureCategory;
    @Column(nullable = false) private boolean retryable;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
