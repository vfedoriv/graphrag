package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.repository;

import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftAggregateRevisionEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftConflictEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSchemaDraftConflictRepository extends JpaRepository<SchemaDraftConflictEntity, String> {
    Optional<SchemaDraftConflictEntity> findByIdAndDraftId(String id, String draftId);
    List<SchemaDraftConflictEntity> findByDraftIdAndAggregateRevisionIdOrderByCoordinateAscIdAsc(
        String draftId, String aggregateRevisionId);

    @Query("""
        select conflict from SchemaDraftConflictEntity conflict
        join SchemaDraftAggregateRevisionEntity aggregateRevision
          on aggregateRevision.id = conflict.aggregateRevisionId
        where conflict.draftId = :draftId
        order by aggregateRevision.revision desc, conflict.coordinate asc, conflict.id asc
        """)
    List<SchemaDraftConflictEntity> findHistory(@Param("draftId") String draftId);

    @Query("""
        select conflict from SchemaDraftConflictEntity conflict
        join SchemaDraftAggregateRevisionEntity aggregateRevision
          on aggregateRevision.id = conflict.aggregateRevisionId
        where conflict.draftId = :draftId
          and conflict.resolved = true
          and conflict.aggregateRevisionId <> :aggregateRevisionId
        order by aggregateRevision.revision desc, conflict.resolvedAt desc,
          conflict.createdAt desc, conflict.id asc
        """)
    List<SchemaDraftConflictEntity> findResolvedHistory(
        @Param("draftId") String draftId,
        @Param("aggregateRevisionId") String aggregateRevisionId
    );
}
