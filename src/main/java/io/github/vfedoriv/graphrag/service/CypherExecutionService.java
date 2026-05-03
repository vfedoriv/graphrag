package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.dto.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.dto.QueryValidationResponse;
import io.github.vfedoriv.graphrag.error.QueryRejectedException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
public class CypherExecutionService {

    private final AppProperties appProperties;
    private final CypherValidationService cypherValidationService;
    private final Neo4jClient neo4jClient;

    public CypherExecutionService(
        AppProperties appProperties,
        CypherValidationService cypherValidationService,
        Neo4jClient neo4jClient
    ) {
        this.appProperties = appProperties;
        this.cypherValidationService = cypherValidationService;
        this.neo4jClient = neo4jClient;
    }

    public QueryExecutionResponse execute(String knowledgeBaseId, String cypher, Map<String, Object> parameters) {
        var validation = cypherValidationService.validate(knowledgeBaseId, cypher, parameters);
        if (!validation.valid()) {
            throw new QueryRejectedException(validation.errors());
        }
        long start = System.nanoTime();
        List<Map<String, Object>> rows = new ArrayList<>(
            neo4jClient.query(validation.cypher()).bindAll(validation.parameters()).fetch().all()
        );
        long executionTimeMs = (System.nanoTime() - start) / 1_000_000;
        List<String> columns = rows.stream()
            .flatMap(row -> row.keySet().stream())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
            .stream()
            .toList();
        return new QueryExecutionResponse(
            validation.cypher(),
            validation.parameters(),
            toValidationResponse(validation),
            columns,
            rows,
            rows.size(),
            executionTimeMs
        );
    }

    QueryValidationResponse toValidationResponse(io.github.vfedoriv.graphrag.query.QueryValidationResult validation) {
        return new QueryValidationResponse(
            validation.valid(),
            validation.cypher(),
            validation.parameters(),
            validation.errors(),
            appProperties.query().maxRows(),
            appProperties.query().timeoutSeconds()
        );
    }
}
