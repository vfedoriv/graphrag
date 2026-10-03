package io.github.vfedoriv.graphrag.documents.adapters.relational.repository;

import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.documents.adapters.relational.entity.DocumentProcessingRunEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface JpaDocumentProcessingRunRepository extends JpaRepository<DocumentProcessingRunEntity, String> {
    List<DocumentProcessingRunEntity> findByDocumentIdOrderByStartedAtAscIdAsc(String documentId);
    boolean existsByDocumentId(String documentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE DocumentProcessingRunEntity run
        SET run.activeCompleted = false,
            run.version = run.version + 1
        WHERE run.documentId = :documentId
          AND run.status = io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunStatus.COMPLETED
          AND run.id <> :activeRunId
          AND run.activeCompleted = true
        """)
    int deactivateOtherCompletedRuns(
        @Param("documentId") String documentId,
        @Param("activeRunId") String activeRunId
    );

    List<DocumentProcessingRunEntity> findByStatusAndStartedAtBeforeOrderByStartedAtAsc(
        DocumentProcessingRunStatus status,
        Instant before,
        Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DocumentProcessingRunEntity run WHERE run.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") String documentId);
}
