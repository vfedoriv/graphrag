package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchemaReprocessingPlanRepository extends Neo4jRepository<SchemaReprocessingPlanNode, String> {
    Optional<SchemaReprocessingPlanNode> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);
    List<SchemaReprocessingPlanNode> findByStatusIn(List<SchemaReprocessingPlanStatus> statuses);

    @Query(value = """
        MATCH (p:SchemaReprocessingPlan {knowledgeBaseId: $knowledgeBaseId})
        RETURN p ORDER BY p.createdAt DESC, p.id DESC SKIP $skip LIMIT $limit
        """, countQuery = """
        MATCH (p:SchemaReprocessingPlan {knowledgeBaseId: $knowledgeBaseId}) RETURN count(p)
        """)
    Page<SchemaReprocessingPlanNode> findPageByKnowledgeBaseId(String knowledgeBaseId, Pageable pageable);

    @Query(value = """
        MATCH (p:SchemaReprocessingPlan {knowledgeBaseId: $knowledgeBaseId, draftId: $draftId})
        RETURN p ORDER BY p.createdAt DESC, p.id DESC SKIP $skip LIMIT $limit
        """, countQuery = """
        MATCH (p:SchemaReprocessingPlan {knowledgeBaseId: $knowledgeBaseId, draftId: $draftId}) RETURN count(p)
        """)
    Page<SchemaReprocessingPlanNode> findPageByKnowledgeBaseIdAndDraftId(
        String knowledgeBaseId, String draftId, Pageable pageable);

    @Query("""
        MATCH (p:SchemaReprocessingPlan) WHERE p.draftId IN $draftIds
        AND NOT EXISTS {
            MATCH (newer:SchemaReprocessingPlan {draftId: p.draftId})
            WHERE newer.createdAt > p.createdAt
               OR (newer.createdAt = p.createdAt AND newer.id > p.id)
        }
        RETURN p
        """)
    List<SchemaReprocessingPlanNode> findLatestForDraftIds(List<String> draftIds);

    @Query("""
        MATCH (p:SchemaReprocessingPlan {id: $planId, status: 'QUEUED'})
        WHERE p.claimedBy IS NULL
        SET p.status = 'RUNNING', p.claimedBy = $workerId, p.claimedAt = $claimedAt,
            p.startedAt = $claimedAt, p.persistenceVersion = coalesce(p.persistenceVersion, 0) + 1
        RETURN count(p)
        """)
    Long claim(String planId, String workerId, Instant claimedAt);
}
