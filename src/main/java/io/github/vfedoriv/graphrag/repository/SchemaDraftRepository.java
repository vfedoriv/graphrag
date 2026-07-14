package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface SchemaDraftRepository extends Neo4jRepository<SchemaDraftNode, String> {
    List<SchemaDraftNode> findByKnowledgeBaseIdOrderByUpdatedAtDesc(String knowledgeBaseId);

    Optional<SchemaDraftNode> findByIdAndKnowledgeBaseId(String id, String knowledgeBaseId);

    @Query("""
        MATCH (kb:KnowledgeBase {id: $knowledgeBaseId}), (d:SchemaDraft {id: $draftId})
        MERGE (kb)-[:OWNS_DRAFT]->(d)
        RETURN count(d)
        """)
    Long attachToKnowledgeBase(String knowledgeBaseId, String draftId);

    @Query("""
        MATCH (d:SchemaDraft {id: $draftId, status: 'OPEN'})
        CREATE (lease:SchemaDraftAnalysisLease {id: $draftId, runId: $runId})
        MERGE (lease)-[:LEASES]->(d)
        SET d.runningAnalysisRunId = $runId, d.persistenceVersion = coalesce(d.persistenceVersion, 0) + 1
        RETURN count(d)
        """)
    Long reserveAnalysis(String draftId, String runId);

    @Query("""
        MATCH (d:SchemaDraft {id: $draftId})
        OPTIONAL MATCH (lease:SchemaDraftAnalysisLease {id: $draftId, runId: $runId})
        DETACH DELETE lease
        WITH d
        REMOVE d.runningAnalysisRunId
        SET d.persistenceVersion = coalesce(d.persistenceVersion, 0) + 1
        RETURN count(d)
        """)
    Long releaseAnalysis(String draftId, String runId);

    @Query("""
        MATCH (d:SchemaDraft {id: $draftId, knowledgeBaseId: $knowledgeBaseId})
        OPTIONAL MATCH (d)<-[:BELONGS_TO_DRAFT]-(child)
        DETACH DELETE child, d
        RETURN count(d)
        """)
    Long deleteOwnedGraph(String knowledgeBaseId, String draftId);
}
