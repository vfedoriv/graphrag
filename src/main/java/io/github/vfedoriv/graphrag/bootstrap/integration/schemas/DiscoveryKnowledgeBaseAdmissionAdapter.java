package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseSchemaAccess;
import io.github.vfedoriv.graphrag.schemas.discovery.ports.DiscoveryKnowledgeBaseAdmission;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryKnowledgeBaseAdmissionAdapter implements DiscoveryKnowledgeBaseAdmission {
    private final KnowledgeBaseSchemaAccess knowledgeBases;

    public DiscoveryKnowledgeBaseAdmissionAdapter(KnowledgeBaseSchemaAccess knowledgeBases) {
        this.knowledgeBases = knowledgeBases;
    }

    @Override
    public void requireManaged(String knowledgeBaseId) {
        knowledgeBases.requireManaged(knowledgeBaseId);
    }
}
