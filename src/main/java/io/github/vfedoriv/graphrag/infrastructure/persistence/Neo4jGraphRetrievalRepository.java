package io.github.vfedoriv.graphrag.infrastructure.persistence;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.error.QueryDeadlineExceededException;
import io.github.vfedoriv.graphrag.repository.GraphRetrievalRepository;
import java.time.Duration;
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
import org.springframework.stereotype.Repository;

@Repository
public class Neo4jGraphRetrievalRepository implements GraphRetrievalRepository {

    private final Driver driver;
    private final AppProperties appProperties;

    public Neo4jGraphRetrievalRepository(Driver driver, AppProperties appProperties) {
        this.driver = driver;
        this.appProperties = appProperties;
    }

    @Override
    public List<Map<String, Object>> execute(Query query, Duration timeout) {
        SessionConfig sessionConfig = SessionConfig.builder()
            .withDatabase(appProperties.neo4j().database())
            .withDefaultAccessMode(AccessMode.READ)
            .build();
        TransactionConfig transactionConfig = TransactionConfig.builder().withTimeout(timeout).build();
        try (Session session = driver.session(sessionConfig)) {
            return session.executeRead(
                transaction -> transaction.run(query.cypher(), query.parameters()).list(this::toMap),
                transactionConfig
            );
        } catch (Neo4jException exception) {
            if (isTimeout(exception)) {
                throw new QueryDeadlineExceededException(exception);
            }
            throw exception;
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
        String normalized = code.toLowerCase(Locale.ROOT);
        return normalized.contains("timeout") || normalized.contains("timedout");
    }
}
