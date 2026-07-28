package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity;

import io.github.vfedoriv.graphrag.domain.ExtractionRunStatus;
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
@Table(name = "extraction_run")
@Getter
@Setter
public class ExtractionRunEntity {
    @Id
    private String id;
    @Column(name = "document_id", nullable = false)
    private String documentId;
    @Column(name = "schema_id", nullable = false)
    private String schemaId;
    @Column(nullable = false)
    private String model;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExtractionRunStatus status;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "retry_of_run_id")
    private String retryOfRunId;
    @Column(name = "claimed_by")
    private String claimedBy;
    @Column(name = "claim_until")
    private Instant claimUntil;
    @Version
    private Long version;
}
