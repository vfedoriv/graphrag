package io.github.vfedoriv.graphrag.knowledgebase.adapters.relational;

import io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.repository.JpaKnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.entity.KnowledgeBaseSchemaEntity;
import io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.entity.KnowledgeBaseSchemaId;
import io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.repository.JpaKnowledgeBaseSchemaRepository;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseSchemaFacts;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseSchemaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalKnowledgeBaseSchemaRepository implements KnowledgeBaseSchemaRepository {
    private final JpaKnowledgeBaseSchemaRepository associations;
    private final JpaKnowledgeBaseRepository knowledgeBases;

    public RelationalKnowledgeBaseSchemaRepository(
        JpaKnowledgeBaseSchemaRepository associations,
        JpaKnowledgeBaseRepository knowledgeBases
    ) {
        this.associations = associations;
        this.knowledgeBases = knowledgeBases;
    }

    @Override
    public List<KnowledgeBaseSchemaFacts.Association> associations(String knowledgeBaseId) {
        return associations.findAllByKnowledgeBaseId(knowledgeBaseId).stream()
            .map(value -> new KnowledgeBaseSchemaFacts.Association(value.getSchemaId(), value.isActive()))
            .toList();
    }

    @Override
    public boolean hasActiveReference(String schemaId) {
        return associations.existsBySchemaIdAndActiveTrue(schemaId);
    }

    @Override
    public long attach(String knowledgeBaseId, String schemaId) {
        KnowledgeBaseSchemaId id = new KnowledgeBaseSchemaId(knowledgeBaseId, schemaId);
        if (associations.existsById(id)) {
            return 0L;
        }
        Instant now = Instant.now();
        KnowledgeBaseSchemaEntity association = new KnowledgeBaseSchemaEntity();
        association.setKnowledgeBaseId(knowledgeBaseId);
        association.setSchemaId(schemaId);
        association.setActive(false);
        association.setCreatedAt(now);
        association.setUpdatedAt(now);
        associations.saveAndFlush(association);
        return 1L;
    }

    @Override
    public void activate(String knowledgeBaseId, String schemaId) {
        knowledgeBases.findByIdForUpdate(knowledgeBaseId)
            .orElseThrow(() -> new IllegalStateException(
                "Knowledge base disappeared during activation: " + knowledgeBaseId));
        attach(knowledgeBaseId, schemaId);
        associations.deactivateAll(knowledgeBaseId);
        KnowledgeBaseSchemaEntity selected = associations
            .findById(new KnowledgeBaseSchemaId(knowledgeBaseId, schemaId))
            .orElseThrow(() -> new IllegalStateException(
                "Schema association disappeared during activation: " + schemaId));
        selected.setActive(true);
        selected.setUpdatedAt(Instant.now());
        associations.saveAndFlush(selected);
    }

    @Override
    public long detach(String schemaId) {
        return associations.deleteBySchemaId(schemaId);
    }
}
