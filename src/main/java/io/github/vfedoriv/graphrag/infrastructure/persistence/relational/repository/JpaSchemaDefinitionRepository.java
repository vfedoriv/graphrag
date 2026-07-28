package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSchemaDefinitionRepository extends JpaRepository<SchemaDefinitionEntity, String> {
    boolean existsByNameAndSchemaVersion(String name, int schemaVersion);
}
