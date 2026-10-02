package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceType;
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
@Table(name = "schema_draft_source")
@Getter
@Setter
public class SchemaDraftSourceEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "draft_id", nullable = false) private String draftId;
    @Column(name = "knowledge_base_id", nullable = false) private String knowledgeBaseId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftSourceType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftSourceStatus status;
    @Column(nullable = false) private long revision;
    @Column(name = "document_id") private String documentId;
    private String name;
    @Column(name = "content_type") private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(name = "content_uri") private String contentUri;
    @Column(nullable = false) private boolean analyzed;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
