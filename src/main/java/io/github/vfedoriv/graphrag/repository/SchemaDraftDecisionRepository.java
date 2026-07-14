package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftDecisionNode;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SchemaDraftDecisionRepository extends Neo4jRepository<SchemaDraftDecisionNode, String> {
    List<SchemaDraftDecisionNode> findByDraftIdOrderBySequenceAsc(String draftId);
    List<SchemaDraftDecisionNode> findByDraftIdAndCandidateIdentityOrderBySequenceDesc(String draftId, String candidateIdentity);
    long countByDraftId(String draftId);
}
