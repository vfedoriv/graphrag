package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational;

import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftSourceEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceType;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.repository.JpaSchemaDraftSourceRepository;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftSourceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDraftSourceRepository implements SchemaDraftSourceRepository {
    private final JpaSchemaDraftSourceRepository repository;

    public RelationalSchemaDraftSourceRepository(JpaSchemaDraftSourceRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SchemaDraftSourceNode> findByDraftIdOrderByCreatedAtAsc(String draftId) {
        return map(repository.findByDraftIdOrderByCreatedAtAscIdAsc(draftId));
    }

    @Override
    public List<SchemaDraftSourceNode> findByDraftIdAndStatusOrderByCreatedAtAsc(
        String draftId, SchemaDraftSourceStatus status
    ) {
        return map(repository.findByDraftIdAndStatusOrderByCreatedAtAscIdAsc(draftId, status));
    }

    @Override
    public boolean existsByDraftIdAndStatus(String draftId, SchemaDraftSourceStatus status) {
        return repository.existsByDraftIdAndStatus(draftId, status);
    }

    @Override
    public Optional<SchemaDraftSourceNode> findByIdAndDraftId(String id, String draftId) {
        return repository.findByIdAndDraftId(id, draftId).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public Optional<SchemaDraftSourceNode> findFirstByDraftIdAndTypeAndSha256AndStatus(
        String draftId, SchemaDraftSourceType type, String sha256, SchemaDraftSourceStatus status
    ) {
        return repository.findFirstByDraftIdAndTypeAndSha256AndStatusOrderByCreatedAtAscIdAsc(
            draftId, type, sha256, status).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public List<SchemaDraftSourceNode> findActiveForDraftIds(List<String> draftIds) {
        if (draftIds.isEmpty()) {
            return List.of();
        }
        return map(repository.findByDraftIdInAndStatusOrderByDraftIdAscCreatedAtAscIdAsc(
            draftIds, SchemaDraftSourceStatus.ACTIVE));
    }

    @Override
    public Optional<SchemaDraftSourceNode> findById(String id) {
        return repository.findById(id).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public SchemaDraftSourceNode save(SchemaDraftSourceNode source) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(source)));
    }

    @Override
    public void delete(SchemaDraftSourceNode source) {
        repository.deleteById(source.getId());
        repository.flush();
    }

    private List<SchemaDraftSourceNode> map(
        List<io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftSourceEntity> values
    ) {
        return values.stream().map(SchemaDraftRelationalMapper::toDomain).toList();
    }
}
