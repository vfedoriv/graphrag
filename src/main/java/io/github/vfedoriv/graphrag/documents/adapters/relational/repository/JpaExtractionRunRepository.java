package io.github.vfedoriv.graphrag.documents.adapters.relational.repository;

import io.github.vfedoriv.graphrag.documents.domain.ExtractionRunStatus;
import io.github.vfedoriv.graphrag.documents.adapters.relational.entity.ExtractionRunEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface JpaExtractionRunRepository extends JpaRepository<ExtractionRunEntity, String> {
    List<ExtractionRunEntity> findByDocumentIdOrderByStartedAtAscIdAsc(String documentId);
    boolean existsByDocumentIdAndStatus(String documentId, ExtractionRunStatus status);

    @Query("SELECT run.id FROM ExtractionRunEntity run WHERE run.documentId = :documentId ORDER BY run.startedAt, run.id")
    List<String> findIdsByDocumentId(@Param("documentId") String documentId);

    @Query("""
        SELECT run.id FROM ExtractionRunEntity run
        WHERE run.documentId = :documentId AND run.status = :status
        ORDER BY run.startedAt, run.id
        """)
    List<String> findIdsByDocumentIdAndStatus(
        @Param("documentId") String documentId,
        @Param("status") ExtractionRunStatus status
    );

    List<ExtractionRunEntity> findByStatusAndStartedAtBeforeOrderByStartedAtAsc(
        ExtractionRunStatus status,
        Instant before,
        Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ExtractionRunEntity run WHERE run.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") String documentId);
}
