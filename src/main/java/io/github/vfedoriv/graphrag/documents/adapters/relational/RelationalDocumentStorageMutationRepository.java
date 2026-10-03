package io.github.vfedoriv.graphrag.documents.adapters.relational;

import io.github.vfedoriv.graphrag.documents.domain.DocumentStorageMutationNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentStorageMutationState;
import io.github.vfedoriv.graphrag.documents.adapters.relational.entity.DocumentStorageMutationEntity;
import io.github.vfedoriv.graphrag.documents.adapters.relational.repository.JpaDocumentStorageMutationRepository;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.documents.ports.DocumentStorageMutationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RelationalDocumentStorageMutationRepository implements DocumentStorageMutationRepository {
    private final JpaDocumentStorageMutationRepository repository;

    public RelationalDocumentStorageMutationRepository(JpaDocumentStorageMutationRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DocumentStorageMutationNode> findByStateOrderByCreatedAtAsc(DocumentStorageMutationState state) {
        return repository.findByStateOrderByCreatedAtAscIdAsc(state).stream()
            .map(DocumentWorkflowRelationalMapper::toDomain)
            .toList();
    }

    @Override
    @RelationalTransactional
    public Long deleteCompletedBefore(Instant before) {
        return (long) repository.deleteCompletedBefore(before);
    }

    @Override
    public Optional<DocumentStorageMutationNode> findById(String id) {
        return repository.findById(id).map(DocumentWorkflowRelationalMapper::toDomain);
    }

    @Override
    public DocumentStorageMutationNode save(DocumentStorageMutationNode mutation) {
        return DocumentWorkflowRelationalMapper.toDomain(
            repository.saveAndFlush(DocumentWorkflowRelationalMapper.toEntity(mutation))
        );
    }

    @Override
    @RelationalTransactional
    public List<DocumentStorageMutationNode> claimPending(
        String workerId,
        Instant now,
        Instant claimUntil,
        int limit
    ) {
        List<DocumentStorageMutationEntity> claimed = repository.findClaimable(now, limit);
        for (DocumentStorageMutationEntity entity : claimed) {
            entity.setClaimedBy(workerId);
            entity.setClaimUntil(claimUntil);
        }
        return repository.saveAllAndFlush(claimed).stream()
            .map(DocumentWorkflowRelationalMapper::toDomain)
            .toList();
    }
}
