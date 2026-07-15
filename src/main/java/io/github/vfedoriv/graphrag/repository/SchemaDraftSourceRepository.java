package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface SchemaDraftSourceRepository extends Neo4jRepository<SchemaDraftSourceNode, String> {
    List<SchemaDraftSourceNode> findByDraftIdOrderByCreatedAtAsc(String draftId);
    List<SchemaDraftSourceNode> findByDraftIdAndStatusOrderByCreatedAtAsc(String draftId, SchemaDraftSourceStatus status);
    Optional<SchemaDraftSourceNode> findByIdAndDraftId(String id, String draftId);
    Optional<SchemaDraftSourceNode> findFirstByDraftIdAndTypeAndSha256AndStatus(
        String draftId, SchemaDraftSourceType type, String sha256, SchemaDraftSourceStatus status
    );

    @Query("""
        MATCH (source:SchemaDraftSource {status: 'ACTIVE'})
        WHERE source.draftId IN $draftIds
        RETURN source ORDER BY source.draftId ASC, source.createdAt ASC, source.id ASC
        """)
    List<SchemaDraftSourceNode> findActiveForDraftIds(List<String> draftIds);
}
