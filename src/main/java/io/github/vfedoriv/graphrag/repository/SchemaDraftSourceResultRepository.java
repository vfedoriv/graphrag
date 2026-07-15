package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface SchemaDraftSourceResultRepository extends Neo4jRepository<SchemaDraftSourceResultNode, String> {
    Optional<SchemaDraftSourceResultNode> findFirstByDraftIdAndReuseKeyAndStatusOrderByCompletedAtDesc(
        String draftId, String reuseKey, SchemaDraftSourceResultStatus status
    );
    List<SchemaDraftSourceResultNode> findByRunIdOrderByCreatedAtAsc(String runId);

    @Query(value = """
        MATCH (result:SchemaDraftSourceResult {runId: $runId})
        RETURN result ORDER BY result.createdAt ASC SKIP $skip LIMIT $limit
        """, countQuery = """
        MATCH (result:SchemaDraftSourceResult {runId: $runId}) RETURN count(result)
        """)
    Page<SchemaDraftSourceResultNode> findPageByRunId(String runId, Pageable pageable);

    @Query("""
        MATCH (draft:SchemaDraft {id: $draftId})
        MATCH (aggregate:SchemaDraftAggregateRevision {id: draft.currentAggregateId, draftId: draft.id})
        MATCH (result:SchemaDraftSourceResult {runId: aggregate.runId, status: 'SUCCEEDED'})
        MATCH (source:SchemaDraftSource {
            id: result.sourceId, draftId: draft.id, status: 'ACTIVE', type: 'DOCUMENT'
        })
        WHERE source.revision = result.sourceRevision AND source.documentId IS NOT NULL
        RETURN DISTINCT source.documentId AS documentId
        """)
    List<String> findContributingDocumentIds(String draftId);
}
