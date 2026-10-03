package io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational.entity;

import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ChunkReprocessingSelection;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanStatus;
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
@Table(name = "schema_reprocessing_plan")
@Getter
@Setter
public class SchemaReprocessingPlanEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Enumerated(EnumType.STRING) @Column(name = "reason", nullable = false)
    private ReprocessingPlanReason reason = ReprocessingPlanReason.SCHEMA_ACTIVATION;
    @Enumerated(EnumType.STRING) @Column(name = "selection_mode")
    private ChunkReprocessingSelection selection;
    @Column(name = "expected_chunker_revision", length = 80) private String expectedChunkerRevision;
    @Column(name = "target_snapshot_json") private String targetSnapshotJson;
    @Column(name = "embedding_space_id", length = 80) private String embeddingSpaceId;
    @Column(name = "draft_id") private String draftId;
    @Column(name = "knowledge_base_id", nullable = false) private String knowledgeBaseId;
    @Column(name = "schema_id") private String schemaId;
    @Column(name = "schema_content_hash", length = 64) private String schemaContentHash;
    @Column(name = "ai_profile_id", nullable = false) private String aiProfileId;
    @Column(name = "ai_profile_revision", nullable = false) private long aiProfileRevision;
    @Column(name = "processing_options_json", nullable = false) private String processingOptionsJson;
    @Column(name = "retry_of_plan_id") private String retryOfPlanId;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaReprocessingPlanStatus status;
    @Column(name = "claimed_by") private String claimedBy;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "claim_until") private Instant claimUntil;
    @Column(name = "total_documents", nullable = false) private int totalDocuments;
    @Column(name = "queued_documents", nullable = false) private int queuedDocuments;
    @Column(name = "running_documents", nullable = false) private int runningDocuments;
    @Column(name = "succeeded_documents", nullable = false) private int succeededDocuments;
    @Column(name = "failed_documents", nullable = false) private int failedDocuments;
    @Column(name = "stale_documents", nullable = false) private int staleDocuments;
    @Column(name = "blocked_documents", nullable = false) private int blockedDocuments;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
