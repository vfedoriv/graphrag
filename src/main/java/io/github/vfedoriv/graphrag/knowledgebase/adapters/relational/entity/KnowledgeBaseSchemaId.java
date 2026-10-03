package io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.entity;

import java.io.Serializable;
import java.util.Objects;

public class KnowledgeBaseSchemaId implements Serializable {
    private String knowledgeBaseId;
    private String schemaId;

    public KnowledgeBaseSchemaId() {}

    public KnowledgeBaseSchemaId(String knowledgeBaseId, String schemaId) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.schemaId = schemaId;
    }

    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(String knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public String getSchemaId() { return schemaId; }
    public void setSchemaId(String schemaId) { this.schemaId = schemaId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KnowledgeBaseSchemaId that)) return false;
        return Objects.equals(knowledgeBaseId, that.knowledgeBaseId) && Objects.equals(schemaId, that.schemaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(knowledgeBaseId, schemaId);
    }
}
