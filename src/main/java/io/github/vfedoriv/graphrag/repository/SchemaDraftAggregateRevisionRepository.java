package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftAggregateRevisionNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface SchemaDraftAggregateRevisionRepository extends Neo4jRepository<SchemaDraftAggregateRevisionNode, String> {
    List<SchemaDraftAggregateRevisionNode> findByDraftIdOrderByRevisionDesc(String draftId);
    Optional<SchemaDraftAggregateRevisionNode> findFirstByDraftIdOrderByRevisionDesc(String draftId);

    @Query("""
        MATCH (current:SchemaDraftAggregateRevision {id: $aggregateId, draftId: $draftId})
        MATCH (prior:SchemaDraftAggregateRevision {draftId: $draftId})
        MATCH (:SchemaDraftAnalysisRun {aggregateRevisionId: prior.id, currentResult: true})
        WHERE prior.revision < current.revision
        RETURN prior
        ORDER BY prior.revision DESC
        LIMIT 1
        """)
    Optional<SchemaDraftAggregateRevisionNode> findLegacyPreviousPromoted(String draftId, String aggregateId);
}
