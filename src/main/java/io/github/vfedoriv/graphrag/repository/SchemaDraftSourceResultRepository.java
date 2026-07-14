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
}
