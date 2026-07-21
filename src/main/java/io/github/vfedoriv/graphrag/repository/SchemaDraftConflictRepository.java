package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface SchemaDraftConflictRepository extends Neo4jRepository<SchemaDraftConflictNode, String> {
    Optional<SchemaDraftConflictNode> findByIdAndDraftId(String id, String draftId);

    @Query("""
        MATCH (conflict:SchemaDraftConflict {draftId: $draftId, aggregateRevisionId: $aggregateRevisionId})
        RETURN conflict
        ORDER BY conflict.coordinate ASC, conflict.id ASC
        """)
    List<SchemaDraftConflictNode> findByAggregateRevision(
        String draftId, String aggregateRevisionId
    );

    @Query("""
        MATCH (conflict:SchemaDraftConflict {draftId: $draftId})
        OPTIONAL MATCH (aggregate:SchemaDraftAggregateRevision {
            id: conflict.aggregateRevisionId, draftId: conflict.draftId
        })
        RETURN conflict
        ORDER BY aggregate.revision DESC, conflict.coordinate ASC, conflict.id ASC
        """)
    List<SchemaDraftConflictNode> findHistory(String draftId);

    @Query("""
        MATCH (conflict:SchemaDraftConflict {draftId: $draftId, resolved: true})
        MATCH (aggregate:SchemaDraftAggregateRevision {
            id: conflict.aggregateRevisionId, draftId: conflict.draftId
        })
        WHERE conflict.aggregateRevisionId <> $aggregateRevisionId
        RETURN conflict
        ORDER BY aggregate.revision DESC, conflict.resolvedAt DESC, conflict.createdAt DESC, conflict.id ASC
        """)
    List<SchemaDraftConflictNode> findResolvedHistory(
        String draftId, String aggregateRevisionId
    );
}
