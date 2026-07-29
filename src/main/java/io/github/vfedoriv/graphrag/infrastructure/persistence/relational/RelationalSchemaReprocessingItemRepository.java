package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemStatus;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaReprocessingItemEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaSchemaReprocessingItemRepository;
import io.github.vfedoriv.graphrag.repository.SchemaReprocessingItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaReprocessingItemRepository implements SchemaReprocessingItemRepository {
    private final JpaSchemaReprocessingItemRepository repository;

    public RelationalSchemaReprocessingItemRepository(JpaSchemaReprocessingItemRepository repository) {
        this.repository = repository;
    }
    @Override public Page<SchemaReprocessingItemNode> findByPlanIdOrderByDocumentIdAsc(
        String planId, Pageable pageable
    ) {
        return repository.findByPlanIdOrderByDocumentIdAsc(planId, pageable)
            .map(SchemaDraftRelationalMapper::toDomain);
    }
    @Override public List<SchemaReprocessingItemNode> findByPlanIdOrderByDocumentIdAsc(String planId) {
        return map(repository.findByPlanIdOrderByDocumentIdAsc(planId));
    }
    @Override public List<SchemaReprocessingItemNode> findExpiredClaims(Instant now) {
        return map(repository.findExpiredClaims(now));
    }
    @Override public Long claim(String itemId, String workerId, Instant claimedAt, Instant claimUntil) {
        return (long) repository.claim(itemId, workerId, claimedAt, claimUntil);
    }
    @Override public Long complete(
        String itemId, String workerId, SchemaReprocessingItemStatus status,
        String failureCategory, boolean retryable, Instant completedAt
    ) {
        return (long) repository.complete(
            itemId, workerId, status, failureCategory, retryable, completedAt);
    }
    @Override public Optional<SchemaReprocessingItemNode> findById(String id) {
        return repository.findById(id).map(SchemaDraftRelationalMapper::toDomain);
    }
    @Override public SchemaReprocessingItemNode save(SchemaReprocessingItemNode item) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(item)));
    }
    private List<SchemaReprocessingItemNode> map(List<SchemaReprocessingItemEntity> values) {
        return values.stream().map(SchemaDraftRelationalMapper::toDomain).toList();
    }
}
