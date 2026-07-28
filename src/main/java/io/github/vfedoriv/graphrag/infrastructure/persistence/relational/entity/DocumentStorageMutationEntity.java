package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity;

import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationState;
import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationType;
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
@Table(name = "document_storage_mutation")
@Getter
@Setter
public class DocumentStorageMutationEntity {
    @Id
    private String id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStorageMutationType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStorageMutationState state;
    @Column(name = "knowledge_base_id", nullable = false)
    private String knowledgeBaseId;
    @Column(name = "document_id", nullable = false)
    private String documentId;
    @Column(name = "content_uri")
    private String contentUri;
    @Column(name = "previous_content_uri")
    private String previousContentUri;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "claimed_by")
    private String claimedBy;
    @Column(name = "claim_until")
    private Instant claimUntil;
    @Version
    private Long version;
}
