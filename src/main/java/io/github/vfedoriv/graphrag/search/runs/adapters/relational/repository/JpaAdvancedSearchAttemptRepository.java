package io.github.vfedoriv.graphrag.search.runs.adapters.relational.repository;

import io.github.vfedoriv.graphrag.search.runs.adapters.relational.entity.AdvancedSearchAttemptEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAdvancedSearchAttemptRepository extends JpaRepository<AdvancedSearchAttemptEntity, String> {
    List<AdvancedSearchAttemptEntity> findByRunIdOrderByCreatedAtAscIdAsc(String runId);
}
