package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity;

import io.github.vfedoriv.graphrag.domain.SchemaDraftDecisionType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftReviewState;
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
@Table(name = "schema_draft_decision")
@Getter
@Setter
public class SchemaDraftDecisionEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "draft_id", nullable = false) private String draftId;
    @Column(nullable = false) private long sequence;
    @Column(name = "draft_revision", nullable = false) private long draftRevision;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftDecisionType type;
    @Enumerated(EnumType.STRING) @Column(name = "review_state", nullable = false) private SchemaDraftReviewState reviewState;
    @Column(name = "candidate_identity") private String candidateIdentity;
    @Column(name = "prior_value_json") private String priorValueJson;
    @Column(name = "resulting_value_json") private String resultingValueJson;
    private String rationale;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
