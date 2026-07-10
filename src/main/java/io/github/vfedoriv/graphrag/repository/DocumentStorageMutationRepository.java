package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.DocumentStorageMutationState;
import java.time.Instant;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface DocumentStorageMutationRepository extends Neo4jRepository<DocumentStorageMutationNode, String> {
    List<DocumentStorageMutationNode> findByStateOrderByCreatedAtAsc(DocumentStorageMutationState state);

    @Query("""
        MATCH (mutation:DocumentStorageMutation)
        WHERE mutation.state IN ['COMPLETED', 'COMPENSATED'] AND mutation.completedAt < $before
        DETACH DELETE mutation
        RETURN count(mutation)
        """)
    Long deleteCompletedBefore(Instant before);
}
