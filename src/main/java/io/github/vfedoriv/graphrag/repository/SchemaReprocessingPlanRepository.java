package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface SchemaReprocessingPlanRepository extends Neo4jRepository<SchemaReprocessingPlanNode, String> {
    Optional<SchemaReprocessingPlanNode> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);
    List<SchemaReprocessingPlanNode> findByStatusIn(List<SchemaReprocessingPlanStatus> statuses);

    @Query("""
        MATCH (p:SchemaReprocessingPlan {id: $planId, status: 'QUEUED'})
        WHERE p.claimedBy IS NULL
        SET p.status = 'RUNNING', p.claimedBy = $workerId, p.claimedAt = $claimedAt,
            p.startedAt = $claimedAt, p.persistenceVersion = coalesce(p.persistenceVersion, 0) + 1
        RETURN count(p)
        """)
    Long claim(String planId, String workerId, Instant claimedAt);
}
