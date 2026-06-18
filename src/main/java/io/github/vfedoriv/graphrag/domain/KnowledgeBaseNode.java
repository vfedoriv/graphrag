package io.github.vfedoriv.graphrag.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("KnowledgeBase")
public class KnowledgeBaseNode {

    @Id
    private String id;
    private String name;
    private String activeSchemaId;
    private String activeAiProfileId;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
}
