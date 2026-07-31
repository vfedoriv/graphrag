package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.AdvancedSearchAttemptEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAdvancedSearchAttemptRepository extends JpaRepository<AdvancedSearchAttemptEntity, String> {
    List<AdvancedSearchAttemptEntity> findByRunIdOrderByCreatedAtAscIdAsc(String runId);
}
