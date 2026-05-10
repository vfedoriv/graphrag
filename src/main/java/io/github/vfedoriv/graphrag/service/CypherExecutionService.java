package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.dto.QueryExecutionResponse;
import io.github.vfedoriv.graphrag.dto.QueryValidationResponse;
import io.github.vfedoriv.graphrag.error.QueryRejectedException;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
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
        log.info(
            "Executing Cypher: knowledgeBaseId={}, cypherLength={}, parameterCount={}",
            knowledgeBaseId,
            LogSanitizer.length(cypher),
            parameters == null ? 0 : parameters.size()
        );
        var validation = cypherValidationService.validate(knowledgeBaseId, cypher, parameters);
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
            neo4jClient.query(validation.cypher()).bindAll(validation.parameters()).fetch().all()
        );
        List<Map<String, Object>> rows = rawRows.stream()
            .map(this::normalizeRow)
            .toList();
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

    private Map<String, Object> normalizeRow(Map<String, Object> rawRow) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawRow.entrySet()) {
            normalized.put(entry.getKey(), normalizeValue(entry.getValue()));
        }
        return normalized;
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Value driverValue) {
            return normalizeValue(driverValue.asObject());
        }
        if (value instanceof Node node) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("_type", "node");
            out.put("id", node.id());
            out.put("elementId", node.elementId());
            out.put("labels", node.labels());
            out.put("properties", normalizeValue(node.asMap()));
            return out;
        }
        if (value instanceof Relationship relationship) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("_type", "relationship");
            out.put("id", relationship.id());
            out.put("elementId", relationship.elementId());
            out.put("type", relationship.type());
            out.put("startNodeElementId", relationship.startNodeElementId());
            out.put("endNodeElementId", relationship.endNodeElementId());
            out.put("properties", normalizeValue(relationship.asMap()));
            return out;
        }
        if (value instanceof Path path) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("_type", "path");
            out.put("length", path.length());
            List<Object> nodes = new ArrayList<>();
            for (Node node : path.nodes()) {
                nodes.add(normalizeValue(node));
            }
            List<Object> relationships = new ArrayList<>();
            for (Relationship relationship : path.relationships()) {
                relationships.add(normalizeValue(relationship));
            }
            out.put("nodes", nodes);
            out.put("relationships", relationships);
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            }
            return out;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> out = new ArrayList<>();
            for (Object item : iterable) {
                out.add(normalizeValue(item));
            }
            return out;
        }
        if (value instanceof Object[] array) {
            List<Object> out = new ArrayList<>(array.length);
            for (Object item : array) {
                out.add(normalizeValue(item));
            }
            return out;
        }
        return value;
    }
}
