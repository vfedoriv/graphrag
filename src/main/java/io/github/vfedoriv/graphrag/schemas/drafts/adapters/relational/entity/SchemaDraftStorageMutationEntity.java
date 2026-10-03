package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationState;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationType;
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
@Table(name = "schema_draft_storage_mutation")
@Getter
@Setter
public class SchemaDraftStorageMutationEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftStorageMutationType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftStorageMutationState state;
    @Column(name = "draft_id") private String draftId;
    @Column(name = "source_id") private String sourceId;
    @Column(name = "content_uri") private String contentUri;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Column(name = "last_error") private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
