package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface KnowledgeBaseRepository extends Neo4jRepository<KnowledgeBaseNode, String> {
    List<KnowledgeBaseNode> findAllByOrderByCreatedAtDesc();

    @Query("""
        MATCH (:KnowledgeBase {activeAiProfileId: $profileId})
        RETURN count(*) > 0
        """)
    Boolean existsAiProfileAssignment(String profileId);

    @Query("""
        MATCH (knowledgeBase:KnowledgeBase {activeAiProfileId: $profileId})
        RETURN knowledgeBase.id AS knowledgeBaseId
        """)
    List<String> findIdsByActiveAiProfileId(String profileId);
}
