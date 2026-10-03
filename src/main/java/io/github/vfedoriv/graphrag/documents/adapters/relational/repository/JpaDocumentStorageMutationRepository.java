package io.github.vfedoriv.graphrag.documents.adapters.relational.repository;

import io.github.vfedoriv.graphrag.documents.domain.DocumentStorageMutationState;
import io.github.vfedoriv.graphrag.documents.adapters.relational.entity.DocumentStorageMutationEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaDocumentStorageMutationRepository extends JpaRepository<DocumentStorageMutationEntity, String> {
    List<DocumentStorageMutationEntity> findByStateOrderByCreatedAtAscIdAsc(DocumentStorageMutationState state);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        DELETE FROM DocumentStorageMutationEntity mutation
        WHERE mutation.state IN (
            io.github.vfedoriv.graphrag.documents.domain.DocumentStorageMutationState.COMPLETED,
            io.github.vfedoriv.graphrag.documents.domain.DocumentStorageMutationState.COMPENSATED
        ) AND mutation.completedAt < :before
        """)
    int deleteCompletedBefore(@Param("before") Instant before);

    @Query(value = """
        SELECT * FROM app.document_storage_mutation
        WHERE state = 'PENDING'
          AND (claim_until IS NULL OR claim_until < :now)
        ORDER BY created_at, id
        FOR UPDATE SKIP LOCKED
        LIMIT :limit
        """, nativeQuery = true)
    List<DocumentStorageMutationEntity> findClaimable(
        @Param("now") Instant now,
        @Param("limit") int limit
    );
}
