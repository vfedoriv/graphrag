package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceResultStatus;
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
@Table(name = "schema_draft_source_result")
@Getter
@Setter
public class SchemaDraftSourceResultEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "draft_id", nullable = false) private String draftId;
    @Column(name = "run_id", nullable = false) private String runId;
    @Column(name = "source_id", nullable = false) private String sourceId;
    @Column(name = "source_revision", nullable = false) private long sourceRevision;
    @Column(name = "source_sha256", length = 64) private String sourceSha256;
    @Column(name = "reuse_key", nullable = false, length = 64) private String reuseKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftSourceResultStatus status;
    @Column(nullable = false) private boolean reused;
    @Column(name = "candidates_json") private String candidatesJson;
    @Column(name = "aliases_json") private String aliasesJson;
    @Column(name = "failure_category") private String failureCategory;
    @Column(name = "failure_code") private String failureCode;
    @Column(nullable = false) private boolean retryable;
    @Column(name = "chunk_count", nullable = false) private int chunkCount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
}
