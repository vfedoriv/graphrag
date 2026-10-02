package io.github.vfedoriv.graphrag.search.runs.adapters.relational;

import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunNode;
import io.github.vfedoriv.graphrag.search.runs.domain.AdvancedSearchRunStatus;
import io.github.vfedoriv.graphrag.search.runs.adapters.relational.entity.AdvancedSearchRunEntity;
import io.github.vfedoriv.graphrag.search.runs.adapters.relational.repository.JpaAdvancedSearchRunRepository;
import io.github.vfedoriv.graphrag.search.runs.ports.AdvancedSearchRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalAdvancedSearchRunRepository implements AdvancedSearchRunRepository {
    private final JpaAdvancedSearchRunRepository repository;

    public RelationalAdvancedSearchRunRepository(JpaAdvancedSearchRunRepository repository) {
        this.repository = repository;
    }

    @Override public AdvancedSearchRunNode save(AdvancedSearchRunNode run) {
        return toDomain(repository.saveAndFlush(toEntity(run)));
    }
    @Override public Optional<AdvancedSearchRunNode> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }
    @Override public Optional<AdvancedSearchRunNode> findOwned(String id, String knowledgeBaseId) {
        return repository.findByIdAndKnowledgeBaseId(id, knowledgeBaseId).map(this::toDomain);
    }
    @Override public Page<AdvancedSearchRunNode> findOwnedPage(
        String knowledgeBaseId, AdvancedSearchRunStatus status, Pageable pageable
    ) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<AdvancedSearchRunEntity> page = status == null
            ? repository.findByKnowledgeBaseId(knowledgeBaseId, sorted)
            : repository.findByKnowledgeBaseIdAndStatus(knowledgeBaseId, status, sorted);
        return page.map(this::toDomain);
    }
    @Override public List<AdvancedSearchRunNode> findActive() {
        return repository.findByStatusIn(List.of(AdvancedSearchRunStatus.QUEUED, AdvancedSearchRunStatus.RUNNING))
            .stream().map(this::toDomain).toList();
    }
    @Override public boolean claim(String runId, String workerId, Instant now) {
        return repository.claim(runId, workerId, now) == 1;
    }
    @Override public boolean cancelQueued(String runId, String knowledgeBaseId, Instant now, Instant expiresAt) {
        return repository.cancelQueued(runId, knowledgeBaseId, now, expiresAt) == 1;
    }
    @Override public boolean requestRunningCancellation(String runId, String knowledgeBaseId, Instant now) {
        return repository.requestRunningCancellation(runId, knowledgeBaseId, now) == 1;
    }
    @Override public int deleteExpired(Instant now, int batchSize) {
        return repository.deleteExpired(now, batchSize);
    }

    private AdvancedSearchRunEntity toEntity(AdvancedSearchRunNode node) {
        AdvancedSearchRunEntity entity = new AdvancedSearchRunEntity();
        entity.setId(node.getId()); entity.setKnowledgeBaseId(node.getKnowledgeBaseId());
        entity.setQueryText(node.getQueryText()); entity.setStatus(node.getStatus()); entity.setStage(node.getStage());
        entity.setRequestedEvidence(node.getRequestedEvidence()); entity.setIncludeEvidenceText(node.isIncludeEvidenceText());
        entity.setSettingsSnapshotJson(node.getSettingsSnapshotJson()); entity.setCompletedBranches(node.getCompletedBranches());
        entity.setActiveAiProfileId(node.getActiveAiProfileId()); entity.setSchemaDefinitionId(node.getSchemaDefinitionId());
        entity.setActiveAiProfileRevision(node.getActiveAiProfileRevision());
        entity.setSchemaContentHash(node.getSchemaContentHash()); entity.setSchemaSnapshotJson(node.getSchemaSnapshotJson());
        entity.setTotalBranches(node.getTotalBranches()); entity.setEvidenceCount(node.getEvidenceCount());
        entity.setCancellationRequestedAt(node.getCancellationRequestedAt()); entity.setFailureCategory(node.getFailureCategory());
        entity.setClaimedBy(node.getClaimedBy()); entity.setClaimedAt(node.getClaimedAt());
        entity.setDeadlineAt(node.getDeadlineAt()); entity.setCreatedAt(node.getCreatedAt()); entity.setStartedAt(node.getStartedAt());
        entity.setCompletedAt(node.getCompletedAt()); entity.setExpiresAt(node.getExpiresAt());
        entity.setPersistenceVersion(node.getPersistenceVersion()); return entity;
    }
    private AdvancedSearchRunNode toDomain(AdvancedSearchRunEntity entity) {
        AdvancedSearchRunNode node = new AdvancedSearchRunNode();
        node.setId(entity.getId()); node.setKnowledgeBaseId(entity.getKnowledgeBaseId()); node.setQueryText(entity.getQueryText());
        node.setStatus(entity.getStatus()); node.setStage(entity.getStage()); node.setRequestedEvidence(entity.getRequestedEvidence());
        node.setIncludeEvidenceText(entity.isIncludeEvidenceText()); node.setSettingsSnapshotJson(entity.getSettingsSnapshotJson());
        node.setActiveAiProfileId(entity.getActiveAiProfileId()); node.setSchemaDefinitionId(entity.getSchemaDefinitionId());
        node.setActiveAiProfileRevision(entity.getActiveAiProfileRevision());
        node.setSchemaContentHash(entity.getSchemaContentHash()); node.setSchemaSnapshotJson(entity.getSchemaSnapshotJson());
        node.setCompletedBranches(entity.getCompletedBranches()); node.setTotalBranches(entity.getTotalBranches());
        node.setEvidenceCount(entity.getEvidenceCount()); node.setCancellationRequestedAt(entity.getCancellationRequestedAt());
        node.setFailureCategory(entity.getFailureCategory()); node.setClaimedBy(entity.getClaimedBy()); node.setClaimedAt(entity.getClaimedAt());
        node.setDeadlineAt(entity.getDeadlineAt()); node.setCreatedAt(entity.getCreatedAt()); node.setStartedAt(entity.getStartedAt());
        node.setCompletedAt(entity.getCompletedAt()); node.setExpiresAt(entity.getExpiresAt());
        node.setPersistenceVersion(entity.getPersistenceVersion()); return node;
    }
}
