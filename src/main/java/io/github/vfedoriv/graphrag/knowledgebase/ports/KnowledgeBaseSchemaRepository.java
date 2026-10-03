package io.github.vfedoriv.graphrag.knowledgebase.ports;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseSchemaFacts;
import java.util.List;

public interface KnowledgeBaseSchemaRepository {
    List<KnowledgeBaseSchemaFacts.Association> associations(String knowledgeBaseId);

    boolean hasActiveReference(String schemaId);

    long attach(String knowledgeBaseId, String schemaId);

    void activate(String knowledgeBaseId, String schemaId);

    long detach(String schemaId);
}
