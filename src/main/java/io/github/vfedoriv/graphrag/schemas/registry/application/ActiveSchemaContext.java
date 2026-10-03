package io.github.vfedoriv.graphrag.schemas.registry.application;

import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;

public record ActiveSchemaContext(
    String knowledgeBaseId,
    String schemaDefinitionId,
    SchemaDefinitionNode schemaDefinition,
    SchemaDocument schema
) {
}
