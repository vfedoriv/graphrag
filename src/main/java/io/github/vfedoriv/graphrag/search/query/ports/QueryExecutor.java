package io.github.vfedoriv.graphrag.search.query.ports;
import io.github.vfedoriv.graphrag.search.query.domain.QueryPolicy;
import java.util.List;
import java.util.Map;
/** Planner inspection and validated read execution returning driver-independent values. */
public interface QueryExecutor {
    void explain(String cypher, Map<String,Object> parameters, QueryPolicy policy);
    List<Map<String,Object>> execute(String cypher, Map<String,Object> parameters, QueryPolicy policy);
}
