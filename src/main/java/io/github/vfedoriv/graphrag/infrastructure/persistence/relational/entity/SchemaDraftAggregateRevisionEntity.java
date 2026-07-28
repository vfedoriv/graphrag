package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity;

import io.github.vfedoriv.graphrag.domain.DiffBaselineType;
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
@Table(name = "schema_draft_aggregate_revision")
@Getter
@Setter
public class SchemaDraftAggregateRevisionEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "draft_id", nullable = false) private String draftId;
    @Column(name = "run_id", nullable = false) private String runId;
    @Column(nullable = false) private long revision;
    @Column(name = "candidates_json", nullable = false) private String candidatesJson;
    @Column(name = "conflicts_json", nullable = false) private String conflictsJson;
    @Column(name = "warnings_json", nullable = false) private String warningsJson;
    @Column(name = "schema_json", nullable = false) private String schemaJson;
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "diff_baseline_type")
    private DiffBaselineType diffBaselineType;
    @Column(name = "diff_baseline_id") private String diffBaselineId;
    @Column(name = "diff_baseline_schema_json") private String diffBaselineSchemaJson;
    @Column(name = "diff_baseline_content_hash", length = 64) private String diffBaselineContentHash;
    @Column(nullable = false) private boolean partial;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
