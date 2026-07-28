package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftAggregateRevisionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSchemaDraftAggregateRevisionRepository
    extends JpaRepository<SchemaDraftAggregateRevisionEntity, String> {
    List<SchemaDraftAggregateRevisionEntity> findByDraftIdOrderByRevisionDesc(String draftId);
    Optional<SchemaDraftAggregateRevisionEntity> findFirstByDraftIdOrderByRevisionDesc(String draftId);

    @Query("""
        select aggregateRevision from SchemaDraftAggregateRevisionEntity aggregateRevision
        where aggregateRevision.draftId = :draftId
          and aggregateRevision.revision < (
              select current.revision from SchemaDraftAggregateRevisionEntity current
              where current.id = :aggregateId and current.draftId = :draftId
          )
          and exists (
              select run.id from SchemaDraftAnalysisRunEntity run
              where run.aggregateRevisionId = aggregateRevision.id and run.currentResult = true
          )
        order by aggregateRevision.revision desc
        limit 1
        """)
    Optional<SchemaDraftAggregateRevisionEntity> findLegacyPreviousPromoted(
        @Param("draftId") String draftId, @Param("aggregateId") String aggregateId);
}
