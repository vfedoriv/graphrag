package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository.JpaDocumentProcessingRunRepository;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;

@Repository
public class RelationalDocumentProcessingRunRepository implements DocumentProcessingRunRepository {
    private final JpaDocumentProcessingRunRepository repository;

    public RelationalDocumentProcessingRunRepository(JpaDocumentProcessingRunRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DocumentProcessingRunNode> findByDocumentIdOrderByStartedAtAsc(String documentId) {
        return repository.findByDocumentIdOrderByStartedAtAscIdAsc(documentId).stream()
            .map(DocumentWorkflowRelationalMapper::toDomain)
            .toList();
    }

    @Override
    public Boolean existsLinkedToDocument(String documentId) {
        return repository.existsByDocumentId(documentId);
    }

    @Override
    public Long attachToDocument(String documentId, String runId) {
        return repository.findById(runId)
            .filter(run -> documentId.equals(run.getDocumentId()))
            .map(run -> 1L)
            .orElse(0L);
    }

    @Override
    @RelationalTransactional
    public Long deactivateOtherCompletedRuns(String documentId, String activeRunId) {
        return (long) repository.deactivateOtherCompletedRuns(documentId, activeRunId);
    }

    @Override
    public Optional<DocumentProcessingRunNode> findById(String id) {
        return repository.findById(id).map(DocumentWorkflowRelationalMapper::toDomain);
    }

    @Override
    public DocumentProcessingRunNode save(DocumentProcessingRunNode run) {
        return DocumentWorkflowRelationalMapper.toDomain(
            repository.saveAndFlush(DocumentWorkflowRelationalMapper.toEntity(run))
        );
    }

    @Override
    public List<DocumentProcessingRunNode> findStaleRunningBefore(Instant before, int limit) {
        return repository.findByStatusAndStartedAtBeforeOrderByStartedAtAsc(
            DocumentProcessingRunStatus.RUNNING,
            before,
            PageRequest.of(0, limit)
        ).stream().map(DocumentWorkflowRelationalMapper::toDomain).toList();
    }

    @Override
    public long deleteByDocumentId(String documentId) {
        return repository.deleteByDocumentId(documentId);
    }
}
