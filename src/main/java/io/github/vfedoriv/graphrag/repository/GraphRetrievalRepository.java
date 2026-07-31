package io.github.vfedoriv.graphrag.repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface GraphRetrievalRepository {

    record Query(String cypher, Map<String, Object> parameters) {
        public Query {
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    List<Map<String, Object>> execute(Query query, Duration timeout);
}
