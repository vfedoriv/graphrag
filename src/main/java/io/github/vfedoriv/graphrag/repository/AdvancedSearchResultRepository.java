package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchResultNode;
import java.util.Optional;

public interface AdvancedSearchResultRepository {
    AdvancedSearchResultNode save(AdvancedSearchResultNode result);
    Optional<AdvancedSearchResultNode> findByRunId(String runId);
}
