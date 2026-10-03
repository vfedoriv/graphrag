package io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "knowledge_base")
public class KnowledgeBaseEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String name;
    @Column(name = "active_ai_profile_id", nullable = false)
    private String activeAiProfileId;
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
    public String getActiveAiProfileId() { return activeAiProfileId; }
    public void setActiveAiProfileId(String activeAiProfileId) { this.activeAiProfileId = activeAiProfileId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
