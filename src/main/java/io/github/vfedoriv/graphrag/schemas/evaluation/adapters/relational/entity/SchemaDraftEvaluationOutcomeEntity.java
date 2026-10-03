package io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational.entity;

import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationOutcomeStatus;
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
@Table(name = "schema_draft_evaluation_outcome")
@Getter
@Setter
public class SchemaDraftEvaluationOutcomeEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "run_id", nullable = false) private String runId;
    @Column(name = "draft_id", nullable = false) private String draftId;
    @Column(name = "document_id", nullable = false) private String documentId;
    @Column(name = "document_sha256", nullable = false, length = 64) private String documentSha256;
    @Column(name = "reuse_key", nullable = false, length = 64) private String reuseKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftEvaluationOutcomeStatus status;
    @Column(nullable = false) private boolean reused;
    @Column(name = "reused_from_outcome_id") private String reusedFromOutcomeId;
    @Column(name = "chunk_count", nullable = false) private int chunkCount;
    @Column(name = "metrics_json") private String metricsJson;
    @Column(name = "evidence_coordinates_json") private String evidenceCoordinatesJson;
    @Column(name = "failure_category") private String failureCategory;
    @Column(nullable = false) private boolean retryable;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
