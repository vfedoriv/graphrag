package io.github.vfedoriv.graphrag.schemas.registry.domain;

import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.domain.SchemaFormat;

import java.time.Instant;

public class SchemaDefinitionNode {

    private String id;
    private Long entityVersion;
    private String name;
    private int version;
    private SchemaSourceType sourceType;
    private SchemaFormat format;
    private String content;
    private String contentHash;
    private SchemaStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getEntityVersion() {
        return entityVersion;
    }

    public void setEntityVersion(Long entityVersion) {
        this.entityVersion = entityVersion;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public SchemaSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SchemaSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public SchemaFormat getFormat() {
        return format;
    }

    public void setFormat(SchemaFormat format) {
        this.format = format;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public SchemaStatus getStatus() {
        return status;
    }

    public void setStatus(SchemaStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
