package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;

public record ActiveSchemaContext(
    String knowledgeBaseId,
    String schemaDefinitionId,
    SchemaDefinitionNode schemaDefinition,
    SchemaDocument schema
) {
}
