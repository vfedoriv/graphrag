package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity;

import io.github.vfedoriv.graphrag.domain.SchemaDraftStatus;
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
@Table(name = "schema_draft")
@Getter
@Setter
public class SchemaDraftEntity {
    @Id private String id;
    @Version @Column(name = "version") private Long persistenceVersion;
    @Column(name = "knowledge_base_id", nullable = false) private String knowledgeBaseId;
    @Column(name = "target_name", nullable = false) private String targetName;
    @Column(name = "target_version", nullable = false) private int targetVersion;
    @Column(name = "base_schema_id") private String baseSchemaId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SchemaDraftStatus status;
    @Column(nullable = false) private long revision;
    @Column(name = "guidance_revision", nullable = false) private long guidanceRevision;
    @Column(name = "guidance_json", nullable = false) private String guidanceJson;
    @Column(name = "guidance_fingerprint", nullable = false, length = 64) private String guidanceFingerprint;
    @Column(name = "current_aggregate_id") private String currentAggregateId;
    @Column(name = "running_analysis_run_id") private String runningAnalysisRunId;
    @Column(name = "publication_schema_id") private String publicationSchemaId;
    @Column(name = "publication_content_hash", length = 64) private String publicationContentHash;
    @Column(name = "active_ai_profile_id", nullable = false) private String activeAiProfileId;
    @Column(name = "active_ai_profile_revision", nullable = false) private long activeAiProfileRevision;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
