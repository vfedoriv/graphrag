package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.error.QueryDeadlineExceededException;
import io.github.vfedoriv.graphrag.query.QueryPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.stereotype.Component;

@Component
public class QueryNeo4jExecutor {

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
            return session.executeRead(
                transaction -> transaction.run(cypher, parameters).list(this::toMap),
                transactionConfig
            );
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
            values.put(key, record.get(key).asObject());
        }
        return values;
    }

    private boolean isTimeout(Neo4jException exception) {
        String code = exception.code();
        if (code == null) {
            return false;
        }
        String normalizedCode = code.toLowerCase(Locale.ROOT);
        return normalizedCode.contains("timeout") || normalizedCode.contains("timedout");
    }
}
