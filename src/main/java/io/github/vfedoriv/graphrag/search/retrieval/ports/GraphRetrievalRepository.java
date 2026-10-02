package io.github.vfedoriv.graphrag.search.retrieval.ports;

import java.time.Duration;
import io.github.vfedoriv.graphrag.search.retrieval.domain.GraphQuery;
import java.util.List;
import java.util.Map;

public interface GraphRetrievalRepository {

    List<Map<String, Object>> execute(GraphQuery query, Duration timeout);
}
