package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentProcessingRunRepository {
    List<DocumentProcessingRunNode> findByDocumentIdOrderByStartedAtAsc(String documentId);
    Boolean existsLinkedToDocument(String documentId);
    Long attachToDocument(String documentId, String runId);
    Long deactivateOtherCompletedRuns(String documentId, String activeRunId);
    Optional<DocumentProcessingRunNode> findById(String id);
    DocumentProcessingRunNode save(DocumentProcessingRunNode run);
    List<DocumentProcessingRunNode> findStaleRunningBefore(Instant before, int limit);
    long deleteByDocumentId(String documentId);
}
