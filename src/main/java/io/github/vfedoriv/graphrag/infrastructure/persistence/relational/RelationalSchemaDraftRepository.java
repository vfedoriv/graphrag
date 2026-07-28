package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaSchemaDraftRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

@Repository
public class RelationalSchemaDraftRepository implements SchemaDraftRepository {
    private final JpaSchemaDraftRepository repository;

    public RelationalSchemaDraftRepository(JpaSchemaDraftRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SchemaDraftNode> findByKnowledgeBaseIdOrderByUpdatedAtDesc(String knowledgeBaseId) {
        return repository.findByKnowledgeBaseIdOrderByUpdatedAtDescIdDesc(knowledgeBaseId).stream()
            .map(SchemaDraftRelationalMapper::toDomain).toList();
    }

    @Override
    public Optional<SchemaDraftNode> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId) {
        return repository.findByIdAndKnowledgeBaseId(id, knowledgeBaseId).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public Optional<SchemaDraftNode> findById(String id) {
        return repository.findById(id).map(SchemaDraftRelationalMapper::toDomain);
    }

    @Override
    public SchemaDraftNode save(SchemaDraftNode draft) {
        return SchemaDraftRelationalMapper.toDomain(
            repository.saveAndFlush(SchemaDraftRelationalMapper.toEntity(draft)));
    }

    @Override
    public Long reserveAnalysis(String draftId, String runId, long expectedRevision) {
        return (long) repository.reserveAnalysis(draftId, runId, expectedRevision);
    }

    @Override
    public Long releaseAnalysis(String draftId, String runId) {
        return (long) repository.releaseAnalysis(draftId, runId);
    }

    @Override
    @RelationalTransactional
    public Long deleteOwnedGraph(String knowledgeBaseId, String draftId) {
        long deleted = repository.deleteByIdAndKnowledgeBaseId(draftId, knowledgeBaseId);
        repository.flush();
        return deleted;
    }
}
