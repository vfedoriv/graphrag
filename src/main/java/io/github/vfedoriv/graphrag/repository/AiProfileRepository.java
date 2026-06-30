package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface AiProfileRepository extends Neo4jRepository<AiProfileNode, String> {

    List<AiProfileNode> findAllByOrderByCreatedAtDesc();

    Optional<AiProfileNode> findFirstByDefaultProfileTrue();

    Boolean existsByDefaultProfileTrue();

    @Query("""
        MATCH (profile:AiProfile)
        WHERE profile.id <> $profileId
        SET profile.defaultProfile = false
        RETURN count(profile)
        """)
    Long unsetDefaultProfileForOthers(String profileId);

    @Query("""
        MATCH (:KnowledgeBase {activeAiProfileId: $profileId})
        RETURN count(*) > 0
        """)
    Boolean existsKnowledgeBaseAssignment(String profileId);
}
