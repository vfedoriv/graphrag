package io.github.vfedoriv.graphrag.schemas.registry.ports;

import java.util.List;

public interface SchemaAssociations {
    List<SchemaAssociation> associations(String knowledgeBaseId);

    boolean hasActiveReference(String schemaId);

    long attach(String knowledgeBaseId, String schemaId);

    void activate(String knowledgeBaseId, String schemaId);

    long detach(String schemaId);
}
