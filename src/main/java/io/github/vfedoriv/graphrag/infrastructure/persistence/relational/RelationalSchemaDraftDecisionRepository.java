package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaDraftDecisionNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaSchemaDraftDecisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftDecisionRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalSchemaDraftDecisionRepository implements SchemaDraftDecisionRepository {
    private final JpaSchemaDraftDecisionRepository repository;

    public RelationalSchemaDraftDecisionRepository(JpaSchemaDraftDecisionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SchemaDraftDecisionNode> findByDraftIdOrderBySequenceAsc(String draftId) {
        return map(repository.findByDraftIdOrderBySequenceAsc(draftId));
    }

    @Override
    public List<SchemaDraftDecisionNode> findByDraftIdAndCandidateIdentityOrderBySequenceDesc(
        String draftId, String candidateIdentity
    ) {
        return map(repository.findByDraftIdAndCandidateIdentityOrderBySequenceDesc(draftId, candidateIdentity));
    }

    @Override
    public long countByDraftId(String draftId) {
        return repository.countByDraftId(draftId);
    }

    @Override
    public SchemaDraftDecisionNode save(SchemaDraftDecisionNode decision) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(decision)));
    }

    private List<SchemaDraftDecisionNode> map(
        List<io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftDecisionEntity> values
    ) {
        return values.stream().map(SchemaDraftRelationalMapper::toDomain).toList();
    }
}
