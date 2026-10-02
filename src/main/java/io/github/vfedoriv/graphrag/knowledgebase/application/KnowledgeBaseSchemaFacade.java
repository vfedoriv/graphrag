package io.github.vfedoriv.graphrag.knowledgebase.application;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseSchemaAccess;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseSchemaFacts;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseSchemaRepository;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseLifecycleService;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseSchemaFacade implements KnowledgeBaseSchemaAccess {
    private final KnowledgeBaseLifecycleService lifecycleService;
    private final KnowledgeBaseSchemaRepository associations;

    public KnowledgeBaseSchemaFacade(
        KnowledgeBaseLifecycleService lifecycleService,
        KnowledgeBaseSchemaRepository associations
    ) {
        this.lifecycleService = lifecycleService;
        this.associations = associations;
    }

    @Override
    public KnowledgeBaseSchemaFacts requireManaged(String knowledgeBaseId) {
        return facts(lifecycleService.requireManaged(knowledgeBaseId));
    }

    @Override
    public KnowledgeBaseSchemaFacts provision(String knowledgeBaseId, String name) {
        return facts(lifecycleService.provision(knowledgeBaseId, name));
    }

    @Override
    public KnowledgeBaseSchemaFacts.Associations associations(String knowledgeBaseId) {
        return new KnowledgeBaseSchemaFacts.Associations(associations.associations(knowledgeBaseId));
    }

    @Override
    public boolean hasActiveReference(String schemaId) {
        return associations.hasActiveReference(schemaId);
    }

    @Override
    public long attach(String knowledgeBaseId, String schemaId) {
        return associations.attach(knowledgeBaseId, schemaId);
    }

    @Override
    public void activate(String knowledgeBaseId, String schemaId) {
        associations.activate(knowledgeBaseId, schemaId);
    }

    @Override
    public long detach(String schemaId) {
        return associations.detach(schemaId);
    }

    private KnowledgeBaseSchemaFacts facts(KnowledgeBaseNode node) {
        return new KnowledgeBaseSchemaFacts(node.getId(), node.getActiveSchemaId());
    }
}
