package io.github.vfedoriv.graphrag.documents.ports;

import io.github.vfedoriv.graphrag.documents.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.documents.domain.ExtractionRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExtractionRunRepository {
    List<ExtractionRunNode> findByDocumentIdOrderByStartedAtAsc(String documentId);
    Boolean hasCompletedRun(String documentId);
    Optional<ExtractionRunNode> findById(String id);
    ExtractionRunNode save(ExtractionRunNode run);
    List<String> findIdsByDocumentId(String documentId);
    List<String> findIdsByDocumentIdAndStatus(String documentId, ExtractionRunStatus status);
    List<ExtractionRunNode> findStaleRunningBefore(Instant before, int limit);
    long deleteByDocumentId(String documentId);
}
