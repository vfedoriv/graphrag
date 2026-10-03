package io.github.vfedoriv.graphrag.schemas.registry.ports;

public interface KnowledgeBaseAdmission {
    SchemaKnowledgeBase requireManaged(String knowledgeBaseId);

    SchemaKnowledgeBase provision(String knowledgeBaseId, String name);
}
