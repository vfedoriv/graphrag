package io.github.vfedoriv.graphrag.schemas.publication.adapters.relational.entity;

import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationStatus;
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
@Table(name = "schema_draft_publication")
@Getter
@Setter
public class SchemaDraftPublicationEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "draft_id", nullable = false) private String draftId;
    @Column(name = "knowledge_base_id", nullable = false) private String knowledgeBaseId;
    @Column(name = "target_identity", nullable = false) private String targetIdentity;
    @Column(name = "draft_revision", nullable = false) private long draftRevision;
    @Column(name = "aggregate_revision_id", nullable = false) private String aggregateRevisionId;
    @Column(name = "projection_content_hash", nullable = false, length = 64) private String projectionContentHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftPublicationStatus status;
    @Column(name = "schema_id") private String schemaId;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Column(name = "failure_category") private String failureCategory;
    @Column(nullable = false) private boolean retryable;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
}
