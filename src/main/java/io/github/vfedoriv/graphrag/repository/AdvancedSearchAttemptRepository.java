package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchAttemptNode;
import java.util.List;

public interface AdvancedSearchAttemptRepository {
    AdvancedSearchAttemptNode save(AdvancedSearchAttemptNode attempt);
    List<AdvancedSearchAttemptNode> findByRunId(String runId);
}
