package io.github.vfedoriv.graphrag.search.retrieval.domain;

import java.util.Map;

/** Immutable rendered graph query; execution remains a retrieval port effect. */
public record GraphQuery(String cypher, Map<String, Object> parameters) {
    public GraphQuery {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
