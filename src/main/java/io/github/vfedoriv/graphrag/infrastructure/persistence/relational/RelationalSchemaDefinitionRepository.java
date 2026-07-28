package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.KnowledgeBaseSchemaEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.KnowledgeBaseSchemaId;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDefinitionEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaKnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaKnowledgeBaseSchemaRepository;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaSchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDefinitionRepository implements SchemaDefinitionRepository {
    private final JpaSchemaDefinitionRepository repository;
    private final JpaKnowledgeBaseSchemaRepository associationRepository;
    private final JpaKnowledgeBaseRepository knowledgeBaseRepository;

    public RelationalSchemaDefinitionRepository(
        JpaSchemaDefinitionRepository repository,
        JpaKnowledgeBaseSchemaRepository associationRepository,
        JpaKnowledgeBaseRepository knowledgeBaseRepository
    ) {
        this.repository = repository;
        this.associationRepository = associationRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    @Override
    public Boolean existsByNameAndVersion(String name, int version) {
        return repository.existsByNameAndSchemaVersion(name, version);
    }

    @Override
    public Optional<SchemaDefinitionNode> findById(String id) {
        return repository.findById(id).map(entity -> toDomain(
            entity,
            associationRepository.existsBySchemaIdAndActiveTrue(id)
        ));
    }

    @Override
    public List<SchemaDefinitionNode> findAll() {
        return repository.findAll().stream()
            .map(entity -> toDomain(entity, associationRepository.existsBySchemaIdAndActiveTrue(entity.getId())))
            .toList();
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
        return toDomain(saved, associationRepository.existsBySchemaIdAndActiveTrue(saved.getId()));
    }

    @Override
    public void delete(SchemaDefinitionNode schema) {
        repository.deleteById(schema.getId());
        repository.flush();
    }

    @Override
    public List<SchemaDefinitionNode> findAllByKnowledgeBaseId(String knowledgeBaseId) {
        List<KnowledgeBaseSchemaEntity> associations =
            associationRepository.findAllByKnowledgeBaseId(knowledgeBaseId);
        Map<String, KnowledgeBaseSchemaEntity> bySchemaId = associations.stream()
            .collect(Collectors.toMap(KnowledgeBaseSchemaEntity::getSchemaId, Function.identity()));
        return repository.findAllById(bySchemaId.keySet()).stream()
            .map(entity -> toDomain(entity, bySchemaId.get(entity.getId()).isActive()))
            .toList();
    }

    @Override
    public Long associateWithKnowledgeBase(String knowledgeBaseId, String schemaId) {
        KnowledgeBaseSchemaId id = new KnowledgeBaseSchemaId(knowledgeBaseId, schemaId);
        if (associationRepository.existsById(id)) return 0L;
        Instant now = Instant.now();
        KnowledgeBaseSchemaEntity association = new KnowledgeBaseSchemaEntity();
        association.setKnowledgeBaseId(knowledgeBaseId);
        association.setSchemaId(schemaId);
        association.setActive(false);
        association.setCreatedAt(now);
        association.setUpdatedAt(now);
        associationRepository.saveAndFlush(association);
        return 1L;
    }

    @Override
    @RelationalTransactional
    public void activateForKnowledgeBase(String knowledgeBaseId, String schemaId) {
        knowledgeBaseRepository.findByIdForUpdate(knowledgeBaseId)
            .orElseThrow(() -> new IllegalStateException("Knowledge base disappeared during activation: " + knowledgeBaseId));
        associateWithKnowledgeBase(knowledgeBaseId, schemaId);
        associationRepository.deactivateAll(knowledgeBaseId);
        KnowledgeBaseSchemaEntity selected = associationRepository
            .findById(new KnowledgeBaseSchemaId(knowledgeBaseId, schemaId))
            .orElseThrow(() -> new IllegalStateException("Schema association disappeared during activation: " + schemaId));
        selected.setActive(true);
        selected.setUpdatedAt(Instant.now());
        associationRepository.saveAndFlush(selected);
    }

    @Override
    public Boolean existsActiveKnowledgeBaseReference(String schemaId) {
        return associationRepository.existsBySchemaIdAndActiveTrue(schemaId);
    }

    @Override
    public Long detachKnowledgeBaseAssociations(String schemaId) {
        return associationRepository.deleteBySchemaId(schemaId);
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

    private SchemaDefinitionNode toDomain(SchemaDefinitionEntity source, boolean active) {
        SchemaDefinitionNode target = new SchemaDefinitionNode();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setVersion(source.getSchemaVersion());
        target.setSourceType(source.getSourceType());
        target.setFormat(source.getFormat());
        target.setContent(source.getContent());
        target.setContentHash(source.getContentHash());
        target.setStatus(active ? SchemaStatus.ACTIVE : SchemaStatus.INACTIVE);
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setEntityVersion(source.getVersion());
        return target;
    }
}
