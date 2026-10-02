package io.github.vfedoriv.graphrag.search.query.application;

import io.github.vfedoriv.graphrag.search.query.application.CypherValidationService;
import io.github.vfedoriv.graphrag.service.AiProfileContext;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchProfiles;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;

import io.github.vfedoriv.graphrag.search.runs.ports.SearchSchemas;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;

import io.github.vfedoriv.graphrag.search.query.api.model.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryValidationResponse;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchProfiles.Profile;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.search.query.ports.CypherGenerationClient;
import io.github.vfedoriv.graphrag.search.query.domain.GeneratedCypher;
import io.github.vfedoriv.graphrag.search.query.domain.QueryValidationResult;
import io.github.vfedoriv.graphrag.search.query.domain.QueryPolicy;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryPolicyResponse;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CypherGenerationService {

    private final RuntimeSettingsService runtimeSettingsService;
    private final SearchSchemas activeSchemaResolver;
    private final ObjectProvider<CypherGenerationClient> cypherGenerationClientProvider;
    private final CypherValidationService cypherValidationService;
    private final AiObservationService aiObservationService;
    private final SearchProfiles knowledgeBaseService;

    public CypherGenerationService(
        RuntimeSettingsService runtimeSettingsService,
        SearchSchemas activeSchemaResolver,
        ObjectProvider<CypherGenerationClient> cypherGenerationClientProvider,
        CypherValidationService cypherValidationService,
        AiObservationService aiObservationService,
        SearchProfiles knowledgeBaseService
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.activeSchemaResolver = activeSchemaResolver;
        this.cypherGenerationClientProvider = cypherGenerationClientProvider;
        this.cypherValidationService = cypherValidationService;
        this.aiObservationService = aiObservationService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public GeneratedQueryResponse generate(String knowledgeBaseId, String prompt) {
        return generate(knowledgeBaseId, prompt, runtimeSettingsService.queryPolicy());
    }

    public GeneratedQueryResponse generate(String knowledgeBaseId, String prompt, QueryPolicy policy) {
        long startNanos = System.nanoTime();
        log.info(
            "Generating Cypher: knowledgeBaseId={}, promptLength={}, promptFingerprint={}",
            knowledgeBaseId,
            LogMetadata.length(prompt),
            LogMetadata.fingerprint(prompt)
        );
        SchemaSnapshot schemaContext = activeSchemaResolver.resolveActive(knowledgeBaseId);
        SchemaDocument schema = schemaContext.schema();

        try (AiObservationScope workflow = aiObservationService.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_CYPHER_GENERATION,
            schema.name(),
            Map.of(
                "knowledge_base.id", String.valueOf(knowledgeBaseId),
                "schema.id", String.valueOf(schemaContext.schemaDefinitionId()),
                "ai.user_prompt.length", String.valueOf(LogMetadata.length(prompt))
            )
        ))) {
            try {
                CypherGenerationClient client = resolveCypherGenerationClient();
                if (client == null) {
                    log.error("Cypher generation client is missing: knowledgeBaseId={}", knowledgeBaseId);
                    throw new IllegalStateException("Cypher generation model is not configured for this profile");
                }
                log.info("Cypher generation client resolved: knowledgeBaseId={}, clientClass={}", knowledgeBaseId, client.getClass().getName());
                Profile activeProfile = knowledgeBaseService.forKnowledgeBase(knowledgeBaseId);
                GeneratedCypher generated = AiProfileContext.withProfile(
                    activeProfile.id(),
                    () -> client.generate(schema, prompt, policy.maxRows())
                );
                QueryValidationResult validation = cypherValidationService.validate(schema, generated.cypher(), generated.parameters(), policy);
                workflow.highCardinalityAttribute("query.validation.valid", String.valueOf(validation.valid()));
                workflow.highCardinalityAttribute("query.validation.error_count", String.valueOf(validation.errors().size()));
                workflow.highCardinalityAttribute("query.cypher.length", String.valueOf(LogMetadata.length(generated.cypher())));
                GeneratedQueryResponse response = new GeneratedQueryResponse(
                    generated.cypher(),
                    generated.explanation(),
                    generated.parameters(),
                    toValidationResponse(validation)
                );
                log.info(
                    "Cypher generated: knowledgeBaseId={}, valid={}, errorCount={}, cypherLength={}, cypherFingerprint={}, elapsedMs={}",
                    knowledgeBaseId,
                    validation.valid(),
                    validation.errors().size(),
                    LogMetadata.length(generated.cypher()),
                    LogMetadata.fingerprint(generated.cypher()),
                    LogMetadata.elapsedMillis(startNanos)
                );
                workflow.success();
                return response;
            } catch (RuntimeException ex) {
                workflow.error(ex);
                throw ex;
            }
        }
    }

    public QueryValidationResponse toValidationResponse(QueryValidationResult result) {
        return new QueryValidationResponse(
            result.valid(),
            result.cypher(),
            result.parameters(),
            result.errors(),
            result.policy().maxRows(),
            result.policy().timeoutSeconds(),
            QueryPolicyResponse.from(result.policy())
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
