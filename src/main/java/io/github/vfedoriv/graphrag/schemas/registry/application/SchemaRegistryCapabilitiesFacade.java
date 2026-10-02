package io.github.vfedoriv.graphrag.schemas.registry.application;

import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaRegistryCapabilities;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaValidationResult;
import io.github.vfedoriv.graphrag.schemas.contracts.StoredSchemaSnapshots;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaAssociations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SchemaRegistryCapabilitiesFacade implements SchemaRegistryCapabilities {
    private final SchemaRegistryService registry;
    private final SchemaParser parser;
    private final SchemaValidator validator;
    private final StoredSchemaSnapshots storedSnapshots;
    private final SchemaAssociations associations;

    public SchemaRegistryCapabilitiesFacade(
        SchemaRegistryService registry,
        SchemaParser parser,
        SchemaValidator validator,
        StoredSchemaSnapshots storedSnapshots,
        SchemaAssociations associations
    ) {
        this.registry = registry;
        this.parser = parser;
        this.validator = validator;
        this.storedSnapshots = storedSnapshots;
        this.associations = associations;
    }

    @Override
    @RelationalTransactional(readOnly = true)
    public SchemaValidationResult parseAndValidate(String json) {
        SchemaDocument parsed = parser.parse(json);
        List<String> errors = validator.validate(parsed);
        return new SchemaValidationResult(parsed, errors);
    }

    @Override
    @RelationalTransactional(readOnly = true)
    public boolean identityExistsGlobally(String name, int version) {
        return registry.identityExistsGlobally(name, version);
    }

    @Override
    @RelationalTransactional(readOnly = true)
    public Optional<SchemaSnapshot> findAssociatedByIdentity(String knowledgeBaseId, String name, int version) {
        return storedSnapshots.associated(knowledgeBaseId).stream()
            .filter(snapshot -> snapshot.name().equals(name) && snapshot.version() == version)
            .findFirst();
    }

    @Override
    @RelationalTransactional(readOnly = true)
    public Optional<SchemaSnapshot> findStoredById(String schemaDefinitionId) {
        return storedSnapshots.findById(schemaDefinitionId)
            .map(snapshot -> withStatus(snapshot, globalStatus(snapshot.schemaDefinitionId())));
    }

    @Override
    @RelationalTransactional
    public SchemaSnapshot registerGeneratedInactive(String json, String knowledgeBaseId) {
        SchemaDefinitionNode schema = registry.createGeneratedInactiveSchema(json, knowledgeBaseId);
        return snapshot(knowledgeBaseId, schema, globalStatus(schema.getId()));
    }

    private SchemaStatus globalStatus(String schemaId) {
        return associations.hasActiveReference(schemaId) ? SchemaStatus.ACTIVE : SchemaStatus.INACTIVE;
    }

    private SchemaSnapshot withStatus(SchemaSnapshot snapshot, SchemaStatus status) {
        return new SchemaSnapshot(
            snapshot.knowledgeBaseId(), snapshot.schemaDefinitionId(), snapshot.name(), snapshot.version(),
            snapshot.sourceType(), snapshot.format(), status, snapshot.content(), snapshot.contentHash(),
            snapshot.createdAt(), snapshot.updatedAt(), snapshot.schema()
        );
    }

    private SchemaSnapshot snapshot(String knowledgeBaseId, SchemaDefinitionNode schema, SchemaStatus status) {
        return new SchemaSnapshot(
            knowledgeBaseId, schema.getId(), schema.getName(), schema.getVersion(), schema.getSourceType(),
            schema.getFormat(), status, schema.getContent(), schema.getContentHash(), schema.getCreatedAt(),
            schema.getUpdatedAt(), null
        );
    }
}
