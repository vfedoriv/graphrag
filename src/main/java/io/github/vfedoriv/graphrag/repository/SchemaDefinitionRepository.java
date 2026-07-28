package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import java.util.List;
import java.util.Optional;

public interface SchemaDefinitionRepository {
    Boolean existsByNameAndVersion(String name, int version);

    Optional<SchemaDefinitionNode> findById(String id);

    List<SchemaDefinitionNode> findAll();

    long count();

    SchemaDefinitionNode save(SchemaDefinitionNode schema);

    void delete(SchemaDefinitionNode schema);

    List<SchemaDefinitionNode> findAllByKnowledgeBaseId(String knowledgeBaseId);

    Long associateWithKnowledgeBase(String knowledgeBaseId, String schemaId);

    void activateForKnowledgeBase(String knowledgeBaseId, String schemaId);

    Boolean existsActiveKnowledgeBaseReference(String schemaId);

    Long detachKnowledgeBaseAssociations(String schemaId);
}
