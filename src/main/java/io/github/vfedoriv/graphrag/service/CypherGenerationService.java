package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.dto.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.dto.QueryValidationResponse;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.query.CypherGenerationClient;
import io.github.vfedoriv.graphrag.query.GeneratedCypher;
import io.github.vfedoriv.graphrag.query.QueryValidationResult;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CypherGenerationService {

    private final AppProperties appProperties;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    private final SchemaParser schemaParser;
    private final ObjectProvider<CypherGenerationClient> cypherGenerationClientProvider;
    private final CypherValidationService cypherValidationService;
    private final AiObservationService aiObservationService;

    public CypherGenerationService(
        AppProperties appProperties,
        KnowledgeBaseRepository knowledgeBaseRepository,
        SchemaDefinitionRepository schemaDefinitionRepository,
        SchemaParser schemaParser,
        ObjectProvider<CypherGenerationClient> cypherGenerationClientProvider,
        CypherValidationService cypherValidationService,
        AiObservationService aiObservationService
    ) {
        this.appProperties = appProperties;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.schemaDefinitionRepository = schemaDefinitionRepository;
        this.schemaParser = schemaParser;
        this.cypherGenerationClientProvider = cypherGenerationClientProvider;
        this.cypherValidationService = cypherValidationService;
        this.aiObservationService = aiObservationService;
    }

    public GeneratedQueryResponse generate(String knowledgeBaseId, String prompt) {
        long startNanos = System.nanoTime();
        log.info(
            "Generating Cypher: knowledgeBaseId={}, promptLength={}, promptPreview={}",
            knowledgeBaseId,
            LogSanitizer.length(prompt),
            LogSanitizer.preview(prompt)
        );
        KnowledgeBaseNode kb = knowledgeBaseRepository.findById(knowledgeBaseId)
            .orElseThrow(() -> new NotFoundException("Knowledge base not found: " + knowledgeBaseId));
        if (kb.getActiveSchemaId() == null || kb.getActiveSchemaId().isBlank()) {
            throw new IllegalStateException("No active schema for knowledge base: " + knowledgeBaseId);
        }
        SchemaDefinitionNode schemaNode = schemaDefinitionRepository.findById(kb.getActiveSchemaId())
            .orElseThrow(() -> new NotFoundException("Schema not found: " + kb.getActiveSchemaId()));
        SchemaDocument schema = schemaParser.parse(schemaNode.getContent());

        try (AiObservationScope workflow = aiObservationService.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_CYPHER_GENERATION,
            schema.name(),
            Map.of(
                "knowledge_base.id", String.valueOf(knowledgeBaseId),
                "schema.id", String.valueOf(schemaNode.getId()),
                "ai.user_prompt.length", String.valueOf(LogSanitizer.length(prompt))
            )
        ))) {
            try {
                CypherGenerationClient client = resolveCypherGenerationClient();
                if (client == null) {
                    log.error("Cypher generation client is missing: knowledgeBaseId={}", knowledgeBaseId);
                    throw new IllegalStateException("Cypher generation model is not configured for this profile");
                }
                log.info("Cypher generation client resolved: knowledgeBaseId={}, clientClass={}", knowledgeBaseId, client.getClass().getName());
                GeneratedCypher generated = client.generate(schema, prompt, appProperties.query().maxRows());
                QueryValidationResult validation = cypherValidationService.validate(schema, generated.cypher(), generated.parameters());
                workflow.highCardinalityAttribute("query.validation.valid", String.valueOf(validation.valid()));
                workflow.highCardinalityAttribute("query.validation.error_count", String.valueOf(validation.errors().size()));
                workflow.highCardinalityAttribute("query.cypher.length", String.valueOf(LogSanitizer.length(generated.cypher())));
                GeneratedQueryResponse response = new GeneratedQueryResponse(
                    generated.cypher(),
                    generated.explanation(),
                    generated.parameters(),
                    toValidationResponse(validation)
                );
                log.info(
                    "Cypher generated: knowledgeBaseId={}, valid={}, errorCount={}, cypherLength={}, elapsedMs={}",
                    knowledgeBaseId,
                    validation.valid(),
                    validation.errors().size(),
                    LogSanitizer.length(generated.cypher()),
                    LogSanitizer.elapsedMillis(startNanos)
                );
                workflow.success();
                return response;
            } catch (RuntimeException ex) {
                workflow.error(ex);
                throw ex;
            }
        }
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

    private CypherGenerationClient resolveCypherGenerationClient() {
        List<CypherGenerationClient> clients = cypherGenerationClientProvider.orderedStream().toList();
        if (clients.isEmpty()) {
            return null;
        }
        if (clients.size() == 1) {
            return clients.getFirst();
        }
        return clients.stream()
            .filter(client -> !client.getClass().getName().contains("SpringAi"))
            .findFirst()
            .orElse(clients.getFirst());
    }
}
