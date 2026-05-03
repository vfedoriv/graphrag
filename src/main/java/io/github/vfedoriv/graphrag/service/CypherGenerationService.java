package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.dto.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.dto.QueryValidationResponse;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.query.CypherGenerationClient;
import io.github.vfedoriv.graphrag.query.QueryValidationResult;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class CypherGenerationService {

    private final AppProperties appProperties;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    private final SchemaParser schemaParser;
    private final ObjectProvider<CypherGenerationClient> cypherGenerationClientProvider;
    private final CypherValidationService cypherValidationService;

    public CypherGenerationService(
        AppProperties appProperties,
        KnowledgeBaseRepository knowledgeBaseRepository,
        SchemaDefinitionRepository schemaDefinitionRepository,
        SchemaParser schemaParser,
        ObjectProvider<CypherGenerationClient> cypherGenerationClientProvider,
        CypherValidationService cypherValidationService
    ) {
        this.appProperties = appProperties;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.schemaDefinitionRepository = schemaDefinitionRepository;
        this.schemaParser = schemaParser;
        this.cypherGenerationClientProvider = cypherGenerationClientProvider;
        this.cypherValidationService = cypherValidationService;
    }

    public GeneratedQueryResponse generate(String knowledgeBaseId, String prompt) {
        KnowledgeBaseNode kb = knowledgeBaseRepository.findById(knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + knowledgeBaseId));
        if (kb.getActiveSchemaId() == null || kb.getActiveSchemaId().isBlank()) {
            throw new IllegalStateException("No active schema for knowledge base: " + knowledgeBaseId);
        }
        var schemaNode = schemaDefinitionRepository.findById(kb.getActiveSchemaId())
            .orElseThrow(() -> new NotFoundException("Schema not found: " + kb.getActiveSchemaId()));
        var schema = schemaParser.parse(schemaNode.getContent());

        CypherGenerationClient client = cypherGenerationClientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Cypher generation model is not configured for this profile");
        }
        var generated = client.generate(schema, prompt, appProperties.query().maxRows());
        QueryValidationResult validation = cypherValidationService.validate(schema, generated.cypher(), generated.parameters());
        return new GeneratedQueryResponse(
            generated.cypher(),
            generated.explanation(),
            generated.parameters(),
            toValidationResponse(validation)
        );
    }

    QueryValidationResponse toValidationResponse(QueryValidationResult result) {
        return new QueryValidationResponse(
            result.valid(),
            result.cypher(),
            result.parameters(),
            result.errors(),
            appProperties.query().maxRows(),
            appProperties.query().timeoutSeconds()
        );
    }
}
