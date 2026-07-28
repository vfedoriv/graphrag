package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity;

import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictType;
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
@Table(name = "schema_draft_conflict")
@Getter
@Setter
public class SchemaDraftConflictEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "draft_id", nullable = false) private String draftId;
    @Column(name = "aggregate_revision_id", nullable = false) private String aggregateRevisionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftConflictType type;
    @Column(nullable = false) private String coordinate;
    @Column(name = "semantic_key", length = 64) private String semanticKey;
    @Column(name = "alternatives_json", nullable = false) private String alternativesJson;
    @Column(name = "evidence_json", nullable = false) private String evidenceJson;
    @Column(nullable = false) private boolean resolved;
    @Column(name = "selected_alternative") private String selectedAlternative;
    @Column(name = "custom_resolution_json") private String customResolutionJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
}
