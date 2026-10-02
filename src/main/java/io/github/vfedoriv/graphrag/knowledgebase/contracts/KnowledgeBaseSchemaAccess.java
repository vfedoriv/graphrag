package io.github.vfedoriv.graphrag.knowledgebase.contracts;

public interface KnowledgeBaseSchemaAccess {
    KnowledgeBaseSchemaFacts requireManaged(String knowledgeBaseId);

    KnowledgeBaseSchemaFacts provision(String knowledgeBaseId, String name);

    KnowledgeBaseSchemaFacts.Associations associations(String knowledgeBaseId);

    boolean hasActiveReference(String schemaId);

    long attach(String knowledgeBaseId, String schemaId);

    void activate(String knowledgeBaseId, String schemaId);

    long detach(String schemaId);
}
