package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchAttemptNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.AdvancedSearchAttemptEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaAdvancedSearchAttemptRepository;
import io.github.vfedoriv.graphrag.repository.AdvancedSearchAttemptRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalAdvancedSearchAttemptRepository implements AdvancedSearchAttemptRepository {
    private final JpaAdvancedSearchAttemptRepository repository;
    public RelationalAdvancedSearchAttemptRepository(JpaAdvancedSearchAttemptRepository repository) { this.repository = repository; }
    @Override public AdvancedSearchAttemptNode save(AdvancedSearchAttemptNode node) {
        AdvancedSearchAttemptEntity entity = new AdvancedSearchAttemptEntity(); entity.setId(node.getId());
        entity.setRunId(node.getRunId()); entity.setRoundNumber(node.getRoundNumber()); entity.setSubqueryId(node.getSubqueryId());
        entity.setRetriever(node.getRetriever()); entity.setStatus(node.getStatus()); entity.setCandidateCount(node.getCandidateCount());
        entity.setLatencyMs(node.getLatencyMs()); entity.setFailureCategory(node.getFailureCategory());
        entity.setCreatedAt(node.getCreatedAt()); entity.setCompletedAt(node.getCompletedAt());
        entity.setPersistenceVersion(node.getPersistenceVersion()); return toDomain(repository.saveAndFlush(entity));
    }
    @Override public List<AdvancedSearchAttemptNode> findByRunId(String runId) {
        return repository.findByRunIdOrderByCreatedAtAscIdAsc(runId).stream().map(this::toDomain).toList();
    }
    private AdvancedSearchAttemptNode toDomain(AdvancedSearchAttemptEntity entity) {
        AdvancedSearchAttemptNode node = new AdvancedSearchAttemptNode(); node.setId(entity.getId()); node.setRunId(entity.getRunId());
        node.setRoundNumber(entity.getRoundNumber()); node.setSubqueryId(entity.getSubqueryId()); node.setRetriever(entity.getRetriever());
        node.setStatus(entity.getStatus()); node.setCandidateCount(entity.getCandidateCount()); node.setLatencyMs(entity.getLatencyMs());
        node.setFailureCategory(entity.getFailureCategory()); node.setCreatedAt(entity.getCreatedAt()); node.setCompletedAt(entity.getCompletedAt());
        node.setPersistenceVersion(entity.getPersistenceVersion()); return node;
    }
}
