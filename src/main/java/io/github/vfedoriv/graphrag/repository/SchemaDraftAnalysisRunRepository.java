package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface SchemaDraftAnalysisRunRepository extends Neo4jRepository<SchemaDraftAnalysisRunNode, String> {
    Optional<SchemaDraftAnalysisRunNode> findFirstByDraftIdAndStatusOrderByCreatedAtDesc(
        String draftId, SchemaDraftAnalysisStatus status
    );
    List<SchemaDraftAnalysisRunNode> findByStatus(SchemaDraftAnalysisStatus status);
    List<SchemaDraftAnalysisRunNode> findByDraftIdOrderByCreatedAtDesc(String draftId);
    Optional<SchemaDraftAnalysisRunNode> findByIdAndDraftId(String id, String draftId);

    @Query("""
        MATCH (r:SchemaDraftAnalysisRun {id: $runId, status: 'RUNNING'})
        WHERE r.claimedBy IS NULL
        SET r.claimedBy = $workerId, r.claimedAt = $claimedAt, r.persistenceVersion = coalesce(r.persistenceVersion, 0) + 1
        RETURN count(r)
        """)
    Long claim(String runId, String workerId, Instant claimedAt);
}
