package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity;

import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemStatus;
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
@Table(name = "schema_reprocessing_item")
@Getter
@Setter
public class SchemaReprocessingItemEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "plan_id", nullable = false) private String planId;
    @Column(name = "document_id", nullable = false) private String documentId;
    @Column(name = "document_sha256", nullable = false, length = 64) private String documentSha256;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaReprocessingItemStatus status;
    @Column(name = "failure_category") private String failureCategory;
    @Column(nullable = false) private boolean retryable;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Column(name = "prior_item_id") private String priorItemId;
    @Column(name = "claimed_by") private String claimedBy;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "claim_until") private Instant claimUntil;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
