package io.github.vfedoriv.graphrag.documents.adapters.relational;

import io.github.vfedoriv.graphrag.documents.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.documents.domain.ExtractionRunStatus;
import io.github.vfedoriv.graphrag.documents.adapters.relational.repository.JpaExtractionRunRepository;
import io.github.vfedoriv.graphrag.documents.ports.ExtractionRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;

@Repository
public class RelationalExtractionRunRepository implements ExtractionRunRepository {
    private final JpaExtractionRunRepository repository;

    public RelationalExtractionRunRepository(JpaExtractionRunRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ExtractionRunNode> findByDocumentIdOrderByStartedAtAsc(String documentId) {
        return repository.findByDocumentIdOrderByStartedAtAscIdAsc(documentId).stream()
            .map(DocumentWorkflowRelationalMapper::toDomain)
            .toList();
    }

    @Override
    public Boolean hasCompletedRun(String documentId) {
        return repository.existsByDocumentIdAndStatus(documentId, ExtractionRunStatus.COMPLETED);
    }

    @Override
    public Optional<ExtractionRunNode> findById(String id) {
        return repository.findById(id).map(DocumentWorkflowRelationalMapper::toDomain);
    }

    @Override
    public ExtractionRunNode save(ExtractionRunNode run) {
        return DocumentWorkflowRelationalMapper.toDomain(
            repository.saveAndFlush(DocumentWorkflowRelationalMapper.toEntity(run))
        );
    }

    @Override
    public List<String> findIdsByDocumentId(String documentId) {
        return repository.findIdsByDocumentId(documentId);
    }

    @Override
    public List<String> findIdsByDocumentIdAndStatus(String documentId, ExtractionRunStatus status) {
        return repository.findIdsByDocumentIdAndStatus(documentId, status);
    }

    @Override
    public List<ExtractionRunNode> findStaleRunningBefore(Instant before, int limit) {
        return repository.findByStatusAndStartedAtBeforeOrderByStartedAtAsc(
            ExtractionRunStatus.RUNNING,
            before,
            PageRequest.of(0, limit)
        ).stream().map(DocumentWorkflowRelationalMapper::toDomain).toList();
    }

    @Override
    public long deleteByDocumentId(String documentId) {
        return repository.deleteByDocumentId(documentId);
    }
}
