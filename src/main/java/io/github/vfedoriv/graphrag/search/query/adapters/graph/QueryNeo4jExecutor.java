package io.github.vfedoriv.graphrag.search.query.adapters.graph;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.search.query.api.error.QueryDeadlineExceededException;
import io.github.vfedoriv.graphrag.search.query.domain.QueryPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import java.util.ArrayList;
import io.github.vfedoriv.graphrag.search.query.ports.QueryExecutor;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.stereotype.Component;

@Component
public class QueryNeo4jExecutor implements QueryExecutor {

    private final Driver driver;
    private final AppProperties appProperties;

    public QueryNeo4jExecutor(Driver driver, AppProperties appProperties) {
        this.driver = driver;
        this.appProperties = appProperties;
    }

    public void explain(String cypher, Map<String, Object> parameters, QueryPolicy policy) {
        execute("EXPLAIN " + cypher, parameters, policy);
    }

    public List<Map<String, Object>> execute(String cypher, Map<String, Object> parameters, QueryPolicy policy) {
        SessionConfig sessionConfig = SessionConfig.builder()
            .withDatabase(appProperties.neo4j().database())
            .withDefaultAccessMode(AccessMode.READ)
            .build();
        TransactionConfig transactionConfig = TransactionConfig.builder()
            .withTimeout(policy.timeout())
            .build();
        try (Session session = driver.session(sessionConfig)) {
            return List.copyOf(session.executeRead(
                transaction -> transaction.run(cypher, parameters).list(this::toMap),
                transactionConfig
            ));
        } catch (Neo4jException ex) {
            if (isTimeout(ex)) {
                throw new QueryDeadlineExceededException(ex);
            }
            throw ex;
        }
    }

    private Map<String, Object> toMap(Record record) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : record.keys()) {
            values.put(key, normalizeValue(record.get(key)));
        }
        return java.util.Collections.unmodifiableMap(values);
    }

    private boolean isTimeout(Neo4jException exception) {
        String code = exception.code();
        if (code == null) {
            return false;
        }
        String normalizedCode = code.toLowerCase(Locale.ROOT);
        return normalizedCode.contains("timeout") || normalizedCode.contains("timedout");
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
            out.put("labels", normalizeValue(node.labels()));
            out.put("properties", normalizeValue(node.asMap()));
            return java.util.Collections.unmodifiableMap(out);
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
            return java.util.Collections.unmodifiableMap(out);
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
            out.put("nodes", java.util.Collections.unmodifiableList(nodes));
            out.put("relationships", java.util.Collections.unmodifiableList(relationships));
            return java.util.Collections.unmodifiableMap(out);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            }
            return java.util.Collections.unmodifiableMap(out);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> out = new ArrayList<>();
            for (Object item : iterable) {
                out.add(normalizeValue(item));
            }
            return java.util.Collections.unmodifiableList(out);
        }
        if (value instanceof Object[] array) {
            List<Object> out = new ArrayList<>(array.length);
            for (Object item : array) {
                out.add(normalizeValue(item));
            }
            return java.util.Collections.unmodifiableList(out);
        }
        return value;
    }
}
