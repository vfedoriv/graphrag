package io.github.vfedoriv.graphrag.schemas.registry.adapters.relational.repository;

import io.github.vfedoriv.graphrag.schemas.registry.adapters.relational.entity.SchemaDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSchemaDefinitionRepository extends JpaRepository<SchemaDefinitionEntity, String> {
    boolean existsByNameAndSchemaVersion(String name, int schemaVersion);
}
