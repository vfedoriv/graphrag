package io.github.vfedoriv.graphrag.schemas.registry.application;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.contracts.StoredSchemaSnapshots;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class StoredSchemaSnapshotsFacade implements StoredSchemaSnapshots {
    private final SchemaDefinitionRepository definitions;
    private final SchemaRegistryService registry;
    private final SchemaParser parser;

    public StoredSchemaSnapshotsFacade(SchemaDefinitionRepository definitions,
        SchemaRegistryService registry, SchemaParser parser) {
        this.definitions = definitions;
        this.registry = registry;
        this.parser = parser;
    }

    @Override
    public Optional<SchemaSnapshot> findById(String schemaId) {
        return definitions.findById(schemaId).map(definition -> snapshot(null, definition, false));
    }

    @Override
    public Optional<SchemaSnapshot> findParsedById(String schemaId) {
        return definitions.findById(schemaId).map(definition -> snapshot(null, definition, true));
    }

    @Override
    public List<SchemaSnapshot> associated(String knowledgeBaseId) {
        return registry.listSchemasByKnowledgeBase(knowledgeBaseId).stream()
            .map(definition -> snapshot(knowledgeBaseId, definition, false)).toList();
    }

    private SchemaSnapshot snapshot(String knowledgeBaseId, SchemaDefinitionNode definition, boolean parsed) {
        return new SchemaSnapshot(knowledgeBaseId, definition.getId(), definition.getName(),
            definition.getVersion(), definition.getSourceType(), definition.getFormat(), definition.getStatus(),
            definition.getContent(), definition.getContentHash(), definition.getCreatedAt(), definition.getUpdatedAt(),
            parsed ? parser.parse(definition.getContent()) : null);
    }
}
