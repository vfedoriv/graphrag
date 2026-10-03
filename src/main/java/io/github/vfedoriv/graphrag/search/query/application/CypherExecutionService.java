package io.github.vfedoriv.graphrag.search.query.application;

import io.github.vfedoriv.graphrag.search.query.ports.QueryExecutor;
import io.github.vfedoriv.graphrag.search.query.application.CypherValidationService;
import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess;

import io.github.vfedoriv.graphrag.search.query.api.model.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryValidationResponse;
import io.github.vfedoriv.graphrag.search.query.api.error.QueryRejectedException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.search.query.domain.QueryValidationResult;
import io.github.vfedoriv.graphrag.search.query.domain.QueryPolicy;
import io.github.vfedoriv.graphrag.search.query.api.model.QueryPolicyResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CypherExecutionService {

    private final RuntimeSettingsAccess runtimeSettingsService;
    private final CypherValidationService cypherValidationService;
    private final QueryExecutor queryNeo4jExecutor;

    public CypherExecutionService(
        RuntimeSettingsAccess runtimeSettingsService,
        CypherValidationService cypherValidationService,
        QueryExecutor queryNeo4jExecutor
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.cypherValidationService = cypherValidationService;
        this.queryNeo4jExecutor = queryNeo4jExecutor;
    }

    public QueryExecutionResponse execute(String knowledgeBaseId, String cypher, Map<String, Object> parameters) {
        return execute(knowledgeBaseId, cypher, parameters, io.github.vfedoriv.graphrag.search.query.domain.QueryPolicy.from(runtimeSettingsService.query()));
    }

    public QueryExecutionResponse execute(
        String knowledgeBaseId,
        String cypher,
        Map<String, Object> parameters,
        QueryPolicy policy
    ) {
        log.info(
            "Executing Cypher: knowledgeBaseId={}, cypherLength={}, parameterCount={}",
            knowledgeBaseId,
            LogMetadata.length(cypher),
            parameters == null ? 0 : parameters.size()
        );
        QueryValidationResult validation = cypherValidationService.validate(knowledgeBaseId, cypher, parameters, policy);
        if (!validation.valid()) {
            log.info(
                "Cypher execution rejected by validation: knowledgeBaseId={}, errorCount={}",
                knowledgeBaseId,
                validation.errors().size()
            );
            throw new QueryRejectedException(validation.errors());
        }
        long start = System.nanoTime();
        List<Map<String, Object>> rawRows = new ArrayList<>(
            queryNeo4jExecutor.execute(validation.cypher(), validation.parameters(), validation.policy())
        );
        List<Map<String, Object>> rows = List.copyOf(rawRows);
        long executionTimeMs = (System.nanoTime() - start) / 1_000_000;
        List<String> columns = rows.stream()
            .flatMap(row -> row.keySet().stream())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
            .stream()
            .toList();
        QueryExecutionResponse response = new QueryExecutionResponse(
            validation.cypher(),
            validation.parameters(),
            toValidationResponse(validation),
            columns,
            rows,
            rows.size(),
            executionTimeMs
        );
        log.info(
            "Cypher executed: knowledgeBaseId={}, rowCount={}, columnCount={}, executionTimeMs={}",
            knowledgeBaseId,
            response.rowCount(),
            response.columns().size(),
            response.executionTimeMs()
        );
        return response;
    }

    public QueryValidationResponse toValidationResponse(io.github.vfedoriv.graphrag.search.query.domain.QueryValidationResult validation) {
        return new QueryValidationResponse(
            validation.valid(),
            validation.cypher(),
            validation.parameters(),
            validation.errors(),
            validation.policy().maxRows(),
            validation.policy().timeoutSeconds(),
            QueryPolicyResponse.from(validation.policy())
        );
    }

}
