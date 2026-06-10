package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import org.springframework.stereotype.Service;

@Service
public class ActiveSchemaResolver {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    private final SchemaParser schemaParser;

    public ActiveSchemaResolver(
        KnowledgeBaseRepository knowledgeBaseRepository,
        SchemaDefinitionRepository schemaDefinitionRepository,
        SchemaParser schemaParser
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.schemaDefinitionRepository = schemaDefinitionRepository;
        this.schemaParser = schemaParser;
    }

    public ActiveSchemaContext resolve(String knowledgeBaseId) {
        KnowledgeBaseNode kb = knowledgeBaseRepository.findById(knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + knowledgeBaseId));
        if (kb.getActiveSchemaId() == null || kb.getActiveSchemaId().isBlank()) {
            throw new IllegalStateException("No active schema for knowledge base: " + kb.getId());
        }
        SchemaDefinitionNode schemaNode = schemaDefinitionRepository.findById(kb.getActiveSchemaId())
            .orElseThrow(() -> new NotFoundException("Schema not found: " + kb.getActiveSchemaId()));
        SchemaDocument schema = schemaParser.parse(schemaNode.getContent());
        return new ActiveSchemaContext(kb.getId(), schemaNode.getId(), schemaNode, schema);
    }
}
