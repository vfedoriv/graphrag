package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity;

import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunStatus;
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
@Table(name = "document_processing_run")
@Getter
@Setter
public class DocumentProcessingRunEntity {
    @Id
    private String id;
    @Column(name = "document_id", nullable = false)
    private String documentId;
    @Column(name = "knowledge_base_id", nullable = false)
    private String knowledgeBaseId;
    @Column(name = "source_sha256", nullable = false)
    private String sourceSha256;
    @Column(name = "parser_id", nullable = false)
    private String parserId;
    @Column(name = "file_format", nullable = false)
    private String fileFormat;
    @Column(name = "requested_options_json", nullable = false)
    private String requestedOptionsJson;
    @Column(name = "saved_defaults_json", nullable = false)
    private String savedDefaultsJson;
    @Column(name = "effective_options_json", nullable = false)
    private String effectiveOptionsJson;
    @Column(name = "chunk_strategy")
    private String chunkStrategy;
    @Column(name = "chunk_strategy_revision")
    private String chunkStrategyRevision;
    @Column(name = "chunk_settings_hash", length = 64)
    private String chunkSettingsHash;
    @Column(name = "tokenizer_id")
    private String tokenizerId;
    @Column(name = "tokenizer_revision")
    private String tokenizerRevision;
    @Column(name = "token_count_mode", length = 32)
    private String tokenCountMode;
    @Column(name = "effective_chunker_revision", length = 80)
    private String effectiveChunkerRevision;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentProcessingRunStatus status;
    @Column(nullable = false)
    private String stage;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "active_completed", nullable = false)
    private boolean activeCompleted;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "retry_of_run_id")
    private String retryOfRunId;
    @Column(name = "claimed_by")
    private String claimedBy;
    @Column(name = "claim_until")
    private Instant claimUntil;
    @Version
    private Long version;
}
