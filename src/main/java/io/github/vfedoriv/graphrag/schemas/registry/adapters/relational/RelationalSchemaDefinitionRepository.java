package io.github.vfedoriv.graphrag.schemas.registry.adapters.relational;

import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.schemas.registry.adapters.relational.entity.SchemaDefinitionEntity;
import io.github.vfedoriv.graphrag.schemas.registry.adapters.relational.repository.JpaSchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDefinitionRepository implements SchemaDefinitionRepository {
    private final JpaSchemaDefinitionRepository repository;
    public RelationalSchemaDefinitionRepository(JpaSchemaDefinitionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Boolean existsByNameAndVersion(String name, int version) {
        return repository.existsByNameAndSchemaVersion(name, version);
    }

    @Override
    public Optional<SchemaDefinitionNode> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<SchemaDefinitionNode> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public SchemaDefinitionNode save(SchemaDefinitionNode schema) {
        Instant now = Instant.now();
        SchemaDefinitionEntity entity = toEntity(schema);
        if (entity.getCreatedAt() == null) entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        SchemaDefinitionEntity saved = repository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    public void delete(SchemaDefinitionNode schema) {
        repository.deleteById(schema.getId());
        repository.flush();
    }

    private SchemaDefinitionEntity toEntity(SchemaDefinitionNode source) {
        SchemaDefinitionEntity target = new SchemaDefinitionEntity();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setSchemaVersion(source.getVersion());
        target.setSourceType(source.getSourceType());
        target.setFormat(source.getFormat());
        target.setContent(source.getContent());
        target.setContentHash(source.getContentHash());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setVersion(source.getEntityVersion());
        return target;
    }

    private SchemaDefinitionNode toDomain(SchemaDefinitionEntity source) {
        SchemaDefinitionNode target = new SchemaDefinitionNode();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setVersion(source.getSchemaVersion());
        target.setSourceType(source.getSourceType());
        target.setFormat(source.getFormat());
        target.setContent(source.getContent());
        target.setContentHash(source.getContentHash());
        target.setStatus(SchemaStatus.INACTIVE);
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setEntityVersion(source.getVersion());
        return target;
    }
}
