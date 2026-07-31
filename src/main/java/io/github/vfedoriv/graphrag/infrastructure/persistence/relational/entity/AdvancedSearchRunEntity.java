package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStage;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "advanced_search_run")
@Getter
@Setter
public class AdvancedSearchRunEntity {
    @Id private String id;
    @Column(name = "knowledge_base_id", nullable = false) private String knowledgeBaseId;
    @Column(name = "query_text", nullable = false, length = 4000) private String queryText;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AdvancedSearchRunStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AdvancedSearchRunStage stage;
    @Column(name = "requested_evidence", nullable = false) private int requestedEvidence;
    @Column(name = "include_evidence_text", nullable = false) private boolean includeEvidenceText;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_snapshot_json", nullable = false, columnDefinition = "jsonb") private String settingsSnapshotJson;
    @Column(name = "completed_branches", nullable = false) private int completedBranches;
    @Column(name = "total_branches", nullable = false) private int totalBranches;
    @Column(name = "evidence_count", nullable = false) private int evidenceCount;
    @Column(name = "cancellation_requested_at") private Instant cancellationRequestedAt;
    @Column(name = "failure_category", length = 80) private String failureCategory;
    @Column(name = "claimed_by") private String claimedBy;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "deadline_at", nullable = false) private Instant deadlineAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Version @Column(name = "version") private Long persistenceVersion;
}
