package io.github.vfedoriv.graphrag.search.runs.ports;

import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchAttemptNode;
import java.util.List;

public interface AdvancedSearchAttemptRepository {
    AdvancedSearchAttemptNode save(AdvancedSearchAttemptNode attempt);
    List<AdvancedSearchAttemptNode> findByRunId(String runId);
}
