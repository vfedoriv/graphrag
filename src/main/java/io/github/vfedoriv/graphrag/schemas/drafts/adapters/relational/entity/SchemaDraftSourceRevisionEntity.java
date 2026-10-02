package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
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
@Table(name = "schema_draft_source_revision")
@Getter
@Setter
public class SchemaDraftSourceRevisionEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "draft_id", nullable = false) private String draftId;
    @Column(name = "source_id", nullable = false) private String sourceId;
    @Column(nullable = false) private long revision;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftSourceStatus status;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(name = "document_id") private String documentId;
    @Column(name = "content_uri") private String contentUri;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
