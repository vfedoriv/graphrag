package io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational.entity;

import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationStatus;
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
@Table(name = "schema_draft_evaluation_run")
@Getter
@Setter
public class SchemaDraftEvaluationRunEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "draft_id", nullable = false) private String draftId;
    @Column(name = "knowledge_base_id", nullable = false) private String knowledgeBaseId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftEvaluationStatus status;
    @Column(name = "draft_revision", nullable = false) private long draftRevision;
    @Column(name = "aggregate_revision_id", nullable = false) private String aggregateRevisionId;
    @Column(name = "projection_json", nullable = false) private String projectionJson;
    @Column(name = "projection_content_hash", nullable = false, length = 64) private String projectionContentHash;
    @Column(name = "guidance_json", nullable = false) private String guidanceJson;
    @Column(name = "decisions_json", nullable = false) private String decisionsJson;
    @Column(name = "decisions_fingerprint", nullable = false, length = 64) private String decisionsFingerprint;
    @Column(name = "document_snapshot_json", nullable = false) private String documentSnapshotJson;
    @Column(name = "ai_profile_id", nullable = false) private String aiProfileId;
    @Column(name = "ai_profile_revision", nullable = false) private long aiProfileRevision;
    @Column(name = "prompt_revision", nullable = false) private String promptRevision;
    @Column(name = "contract_revision", nullable = false) private String contractRevision;
    @Column(name = "settings_json", nullable = false) private String settingsJson;
    @Column(name = "snapshot_fingerprint", nullable = false, length = 64) private String snapshotFingerprint;
    @Column(name = "retry_of_run_id") private String retryOfRunId;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Column(name = "claimed_by") private String claimedBy;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "claim_until") private Instant claimUntil;
    @Column(name = "total_documents", nullable = false) private int totalDocuments;
    @Column(name = "succeeded_documents", nullable = false) private int succeededDocuments;
    @Column(name = "failed_documents", nullable = false) private int failedDocuments;
    @Column(name = "stale_documents", nullable = false) private int staleDocuments;
    @Column(name = "metrics_json") private String metricsJson;
    @Column(name = "advisory_json") private String advisoryJson;
    @Column(name = "failure_category") private String failureCategory;
    @Column(nullable = false) private boolean retryable;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
