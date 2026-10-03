package io.github.vfedoriv.graphrag.knowledgebase.domain;

import java.time.Instant;

public class KnowledgeBaseNode {

    private String id;
    private Long version;
    private String name;
    private String activeSchemaId;
    private String activeAiProfileId;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getActiveSchemaId() {
        return activeSchemaId;
    }

    public void setActiveSchemaId(String activeSchemaId) {
        this.activeSchemaId = activeSchemaId;
    }

    public String getActiveAiProfileId() {
        return activeAiProfileId;
    }

    public void setActiveAiProfileId(String activeAiProfileId) {
        this.activeAiProfileId = activeAiProfileId;
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
