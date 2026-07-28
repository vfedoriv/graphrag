package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftDecisionNode;
import java.util.List;

public interface SchemaDraftDecisionRepository {
    List<SchemaDraftDecisionNode> findByDraftIdOrderBySequenceAsc(String draftId);
    List<SchemaDraftDecisionNode> findByDraftIdAndCandidateIdentityOrderBySequenceDesc(String draftId, String candidateIdentity);
    long countByDraftId(String draftId);
    SchemaDraftDecisionNode save(SchemaDraftDecisionNode decision);
}
