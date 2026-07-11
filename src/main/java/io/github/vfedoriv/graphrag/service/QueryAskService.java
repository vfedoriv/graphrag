package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.dto.GeneratedQueryResponse;
import io.github.vfedoriv.graphrag.dto.QueryAskResponse;
import io.github.vfedoriv.graphrag.dto.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.error.QueryRejectedException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.query.QueryPolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class QueryAskService {

    private final CypherGenerationService cypherGenerationService;
    private final CypherExecutionService cypherExecutionService;
    private final AiObservationService aiObservationService;
    private final RuntimeSettingsService runtimeSettingsService;

    public QueryAskService(
        CypherGenerationService cypherGenerationService,
        CypherExecutionService cypherExecutionService,
        AiObservationService aiObservationService,
        RuntimeSettingsService runtimeSettingsService
    ) {
        this.cypherGenerationService = cypherGenerationService;
        this.cypherExecutionService = cypherExecutionService;
        this.aiObservationService = aiObservationService;
        this.runtimeSettingsService = runtimeSettingsService;
    }

    public QueryAskResponse ask(String knowledgeBaseId, String prompt) {
        QueryPolicy policy = runtimeSettingsService.queryPolicy();
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("knowledge_base.id", knowledgeBaseId);
        attributes.put("query.prompt.length", String.valueOf(LogMetadata.length(prompt)));
        try (AiObservationScope workflow = aiObservationService.startWorkflow(
            new AiWorkflowContext(AiObservationService.WORKFLOW_QUERY, null, attributes)
        )) {
            try {
                GeneratedQueryResponse generated = cypherGenerationService.generate(knowledgeBaseId, prompt, policy);
                workflow.highCardinalityAttribute("query.validation.valid", String.valueOf(generated.validation().valid()));
                workflow.highCardinalityAttribute("query.validation.error_count", String.valueOf(generated.validation().errors().size()));
                workflow.highCardinalityAttribute("query.cypher.length", String.valueOf(LogMetadata.length(generated.cypher())));
                if (!generated.validation().valid()) {
                    log.info(
                        "Ask query generated invalid Cypher: knowledgeBaseId={}, errorCount={}",
                        knowledgeBaseId,
                        generated.validation().errors().size()
                    );
                    QueryRejectedException exception = new QueryRejectedException(generated.validation().errors());
                    workflow.error(exception);
                    throw exception;
                }
                QueryExecutionResponse execution = cypherExecutionService.execute(
                    knowledgeBaseId,
                    generated.validation().cypher(),
                    generated.validation().parameters(),
                    policy
                );
                workflow.highCardinalityAttribute("query.execution.row_count", String.valueOf(execution.rowCount()));
                workflow.highCardinalityAttribute("query.execution.time_ms", String.valueOf(execution.executionTimeMs()));
                workflow.success();
                log.info(
                    "Ask query completed: knowledgeBaseId={}, rowCount={}, executionTimeMs={}",
                    knowledgeBaseId,
                    execution.rowCount(),
                    execution.executionTimeMs()
                );
                return new QueryAskResponse(generated, execution);
            } catch (QueryRejectedException e) {
                throw e;
            } catch (RuntimeException e) {
                workflow.error(e);
                log.warn(
                    "Ask query failed: knowledgeBaseId={}, exceptionType={}",
                    knowledgeBaseId,
                    LogMetadata.exceptionType(e)
                );
                throw e;
            }
        }
    }
}
