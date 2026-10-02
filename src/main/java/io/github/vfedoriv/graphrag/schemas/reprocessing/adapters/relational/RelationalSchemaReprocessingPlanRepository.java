package io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational;

import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanStatus;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ChunkReprocessingSelection;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ReprocessingPlanReason;
import io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational.entity.SchemaReprocessingPlanEntity;
import io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational.repository.JpaSchemaReprocessingPlanRepository;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.SchemaReprocessingPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaReprocessingPlanRepository implements SchemaReprocessingPlanRepository {
    private final JpaSchemaReprocessingPlanRepository repository;

    public RelationalSchemaReprocessingPlanRepository(JpaSchemaReprocessingPlanRepository repository) {
        this.repository = repository;
    }
    @Override public Optional<SchemaReprocessingPlanNode> findByIdAndKnowledgeBaseId(
        String id, String knowledgeBaseId
    ) {
        return repository.findByIdAndKnowledgeBaseId(id, knowledgeBaseId)
            .map(ReprocessingRelationalMapper::toDomain);
    }
    @Override public boolean existsActiveByKnowledgeBaseId(String knowledgeBaseId) {
        return repository.existsByKnowledgeBaseIdAndStatusIn(
            knowledgeBaseId,
            List.of(SchemaReprocessingPlanStatus.QUEUED, SchemaReprocessingPlanStatus.RUNNING)
        );
    }
    @Override public List<SchemaReprocessingPlanNode> findByStatusIn(List<SchemaReprocessingPlanStatus> statuses) {
        return map(repository.findByStatusIn(statuses));
    }
    @Override public Page<SchemaReprocessingPlanNode> findPageByKnowledgeBaseId(
        String knowledgeBaseId, Pageable pageable
    ) {
        return repository.findByKnowledgeBaseId(knowledgeBaseId, deterministic(pageable))
            .map(ReprocessingRelationalMapper::toDomain);
    }
    @Override public Page<SchemaReprocessingPlanNode> findPageByKnowledgeBaseIdAndDraftId(
        String knowledgeBaseId, String draftId, Pageable pageable
    ) {
        return repository.findByKnowledgeBaseIdAndDraftId(knowledgeBaseId, draftId, deterministic(pageable))
            .map(ReprocessingRelationalMapper::toDomain);
    }
    @Override public Page<SchemaReprocessingPlanNode> findPageByFilters(
        String knowledgeBaseId,
        String draftId,
        ReprocessingPlanReason reason,
        ChunkReprocessingSelection selection,
        SchemaReprocessingPlanStatus status,
        Pageable pageable
    ) {
        return repository.findPageByFilters(
            knowledgeBaseId, draftId, reason, selection, status, deterministic(pageable)
        ).map(ReprocessingRelationalMapper::toDomain);
    }
    @Override public List<SchemaReprocessingPlanNode> findLatestForDraftIds(List<String> draftIds) {
        return draftIds.isEmpty() ? List.of() : map(repository.findLatestForDraftIds(draftIds));
    }
    @Override public Long claim(String planId, String workerId, Instant claimedAt, Instant claimUntil) {
        return (long) repository.claim(planId, workerId, claimedAt, claimUntil);
    }
    @Override public Optional<SchemaReprocessingPlanNode> findById(String id) {
        return repository.findById(id).map(ReprocessingRelationalMapper::toDomain);
    }
    @Override public SchemaReprocessingPlanNode save(SchemaReprocessingPlanNode plan) {
        return ReprocessingRelationalMapper.toDomain(
            repository.saveAndFlush(ReprocessingRelationalMapper.toEntity(plan)));
    }
    private Pageable deterministic(Pageable pageable) {
        return pageable.getSort().isSorted() ? pageable : PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
    private List<SchemaReprocessingPlanNode> map(List<SchemaReprocessingPlanEntity> values) {
        return values.stream().map(ReprocessingRelationalMapper::toDomain).toList();
    }
}
