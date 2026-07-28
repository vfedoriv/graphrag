package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity;

import io.github.vfedoriv.graphrag.domain.SchemaFormat;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "schema_definition", uniqueConstraints = {
    @UniqueConstraint(name = "schema_definition_identity_unique", columnNames = {"name", "schema_version"})
})
public class SchemaDefinitionEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String name;
    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SchemaSourceType sourceType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SchemaFormat format;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public SchemaSourceType getSourceType() { return sourceType; }
    public void setSourceType(SchemaSourceType sourceType) { this.sourceType = sourceType; }
    public SchemaFormat getFormat() { return format; }
    public void setFormat(SchemaFormat format) { this.format = format; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
