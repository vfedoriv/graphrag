package io.github.vfedoriv.graphrag.schemas.registry.ports;

import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import java.util.List;
import java.util.Optional;

public interface SchemaDefinitionRepository {
    Boolean existsByNameAndVersion(String name, int version);

    Optional<SchemaDefinitionNode> findById(String id);

    List<SchemaDefinitionNode> findAll();

    long count();

    SchemaDefinitionNode save(SchemaDefinitionNode schema);

    void delete(SchemaDefinitionNode schema);

}
