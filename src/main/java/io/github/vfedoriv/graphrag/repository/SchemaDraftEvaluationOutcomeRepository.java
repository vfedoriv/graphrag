package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SchemaDraftEvaluationOutcomeRepository extends Neo4jRepository<SchemaDraftEvaluationOutcomeNode, String> {
    Page<SchemaDraftEvaluationOutcomeNode> findByRunIdOrderByDocumentIdAsc(String runId, Pageable pageable);
    java.util.List<SchemaDraftEvaluationOutcomeNode> findByRunIdOrderByDocumentIdAsc(String runId);
    Optional<SchemaDraftEvaluationOutcomeNode> findFirstByDraftIdAndReuseKeyAndStatusInOrderByCompletedAtDesc(
        String draftId, String reuseKey, java.util.List<SchemaDraftEvaluationOutcomeStatus> statuses);
}
