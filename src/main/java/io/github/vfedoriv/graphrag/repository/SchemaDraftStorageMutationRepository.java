package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationState;
import java.time.Instant;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface SchemaDraftStorageMutationRepository extends Neo4jRepository<SchemaDraftStorageMutationNode, String> {
    List<SchemaDraftStorageMutationNode> findByStateOrderByCreatedAtAsc(SchemaDraftStorageMutationState state);

    @Query("MATCH (m:SchemaDraftStorageMutation {state: 'COMPLETED'}) WHERE m.completedAt < $before DETACH DELETE m")
    void deleteCompletedBefore(Instant before);
}
