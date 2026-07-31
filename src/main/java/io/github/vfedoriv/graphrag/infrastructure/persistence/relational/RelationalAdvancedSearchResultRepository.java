package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchResultNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.AdvancedSearchResultEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaAdvancedSearchResultRepository;
import io.github.vfedoriv.graphrag.repository.AdvancedSearchResultRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalAdvancedSearchResultRepository implements AdvancedSearchResultRepository {
    private final JpaAdvancedSearchResultRepository repository;
    public RelationalAdvancedSearchResultRepository(JpaAdvancedSearchResultRepository repository) { this.repository = repository; }
    @Override public AdvancedSearchResultNode save(AdvancedSearchResultNode node) {
        AdvancedSearchResultEntity entity = new AdvancedSearchResultEntity();
        entity.setRunId(node.getRunId()); entity.setPayloadVersion(node.getPayloadVersion());
        entity.setResultJson(node.getResultJson()); entity.setEvidenceCount(node.getEvidenceCount());
        entity.setCreatedAt(node.getCreatedAt()); entity.setPersistenceVersion(node.getPersistenceVersion());
        return toDomain(repository.saveAndFlush(entity));
    }
    @Override public Optional<AdvancedSearchResultNode> findByRunId(String runId) {
        return repository.findById(runId).map(this::toDomain);
    }
    private AdvancedSearchResultNode toDomain(AdvancedSearchResultEntity entity) {
        AdvancedSearchResultNode node = new AdvancedSearchResultNode(); node.setRunId(entity.getRunId());
        node.setPayloadVersion(entity.getPayloadVersion()); node.setResultJson(entity.getResultJson());
        node.setEvidenceCount(entity.getEvidenceCount()); node.setCreatedAt(entity.getCreatedAt());
        node.setPersistenceVersion(entity.getPersistenceVersion()); return node;
    }
}
