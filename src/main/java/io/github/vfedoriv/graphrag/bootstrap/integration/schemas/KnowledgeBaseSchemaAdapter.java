package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseSchemaAccess;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseSchemaFacts;
import io.github.vfedoriv.graphrag.schemas.registry.ports.KnowledgeBaseAdmission;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaAssociation;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaAssociations;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaKnowledgeBase;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseSchemaAdapter implements KnowledgeBaseAdmission, SchemaAssociations {
    private final KnowledgeBaseSchemaAccess access;

    public KnowledgeBaseSchemaAdapter(KnowledgeBaseSchemaAccess access) {
        this.access = access;
    }

    @Override
    public SchemaKnowledgeBase requireManaged(String knowledgeBaseId) {
        return map(access.requireManaged(knowledgeBaseId));
    }

    @Override
    public SchemaKnowledgeBase provision(String knowledgeBaseId, String name) {
        return map(access.provision(knowledgeBaseId, name));
    }

    @Override
    public List<SchemaAssociation> associations(String knowledgeBaseId) {
        return access.associations(knowledgeBaseId).values().stream()
            .map(value -> new SchemaAssociation(value.schemaId(), value.active())).toList();
    }

    @Override
    public boolean hasActiveReference(String schemaId) {
        return access.hasActiveReference(schemaId);
    }

    @Override
    public long attach(String knowledgeBaseId, String schemaId) {
        return access.attach(knowledgeBaseId, schemaId);
    }

    @Override
    public void activate(String knowledgeBaseId, String schemaId) {
        access.activate(knowledgeBaseId, schemaId);
    }

    @Override
    public long detach(String schemaId) {
        return access.detach(schemaId);
    }

    private SchemaKnowledgeBase map(KnowledgeBaseSchemaFacts facts) {
        return new SchemaKnowledgeBase(facts.knowledgeBaseId(), facts.activeSchemaId());
    }
}
