package io.github.vfedoriv.graphrag.schemas.registry.application;

import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshots;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.schemas.registry.ports.KnowledgeBaseAdmission;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaKnowledgeBase;
import org.springframework.stereotype.Service;

@Service
public class ActiveSchemaResolver implements SchemaSnapshots {
    private final KnowledgeBaseAdmission knowledgeBaseAdmission;
    private final SchemaDefinitionRepository definitions;
    private final SchemaParser parser;

    public ActiveSchemaResolver(
        KnowledgeBaseAdmission knowledgeBaseAdmission,
        SchemaDefinitionRepository definitions,
        SchemaParser parser
    ) {
        this.knowledgeBaseAdmission = knowledgeBaseAdmission;
        this.definitions = definitions;
        this.parser = parser;
    }

    @Override
    public SchemaSnapshot resolveActive(String knowledgeBaseId) {
        SchemaKnowledgeBase knowledgeBase = knowledgeBaseAdmission.requireManaged(knowledgeBaseId);
        String schemaId = knowledgeBase.activeSchemaId();
        if (schemaId == null || schemaId.isBlank()) {
            throw new IllegalStateException("No active schema for knowledge base: " + knowledgeBase.id());
        }
        return snapshot(knowledgeBaseId, definition(schemaId));
    }

    @Override
    public SchemaSnapshot resolveExpectedSnapshot(
        String knowledgeBaseId,
        String schemaId,
        String schemaContentHash
    ) {
        SchemaKnowledgeBase knowledgeBase = knowledgeBaseAdmission.requireManaged(knowledgeBaseId);
        if (!schemaId.equals(knowledgeBase.activeSchemaId())) {
            throw new IllegalStateException("Active schema no longer matches immutable processing target");
        }
        SchemaDefinitionNode definition = definition(schemaId);
        if (!schemaContentHash.equals(definition.getContentHash())) {
            throw new IllegalStateException("Schema content no longer matches immutable processing target");
        }
        return snapshot(knowledgeBaseId, definition);
    }

    /** Named compatibility entry point for search callers pending roadmap step 8. */
    public ActiveSchemaContext resolve(String knowledgeBaseId) {
        return legacy(resolveActive(knowledgeBaseId));
    }

    /** Named compatibility entry point for search callers pending roadmap step 8. */
    public ActiveSchemaContext resolveExpected(String knowledgeBaseId, String schemaId, String schemaContentHash) {
        return legacy(resolveExpectedSnapshot(knowledgeBaseId, schemaId, schemaContentHash));
    }

    private SchemaDefinitionNode definition(String schemaId) {
        return definitions.findById(schemaId)
            .orElseThrow(() -> new NotFoundException("Schema not found: " + schemaId));
    }

    private SchemaSnapshot snapshot(String knowledgeBaseId, SchemaDefinitionNode definition) {
        return new SchemaSnapshot(
            knowledgeBaseId, definition.getId(), definition.getName(), definition.getVersion(),
            definition.getSourceType(), definition.getFormat(), definition.getStatus(),
            definition.getContent(), definition.getContentHash(), definition.getCreatedAt(),
            definition.getUpdatedAt(), parser.parse(definition.getContent())
        );
    }

    private ActiveSchemaContext legacy(SchemaSnapshot snapshot) {
        SchemaDefinitionNode definition = new SchemaDefinitionNode();
        definition.setId(snapshot.schemaDefinitionId());
        definition.setName(snapshot.name());
        definition.setVersion(snapshot.version());
        definition.setSourceType(snapshot.sourceType());
        definition.setFormat(snapshot.format());
        definition.setStatus(snapshot.status());
        definition.setContent(snapshot.content());
        definition.setContentHash(snapshot.contentHash());
        definition.setCreatedAt(snapshot.createdAt());
        definition.setUpdatedAt(snapshot.updatedAt());
        return new ActiveSchemaContext(
            snapshot.knowledgeBaseId(), snapshot.schemaDefinitionId(), definition, snapshot.schema());
    }
}
