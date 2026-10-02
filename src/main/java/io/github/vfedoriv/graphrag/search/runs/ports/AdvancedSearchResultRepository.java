package io.github.vfedoriv.graphrag.search.runs.ports;

import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchResultNode;
import java.util.Optional;

public interface AdvancedSearchResultRepository {
    AdvancedSearchResultNode save(AdvancedSearchResultNode result);
    Optional<AdvancedSearchResultNode> findByRunId(String runId);
}
