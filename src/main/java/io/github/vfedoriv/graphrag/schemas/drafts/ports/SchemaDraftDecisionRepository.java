package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftDecisionNode;
import java.util.List;

public interface SchemaDraftDecisionRepository {
    List<SchemaDraftDecisionNode> findByDraftIdOrderBySequenceAsc(String draftId);
    List<SchemaDraftDecisionNode> findByDraftIdAndCandidateIdentityOrderBySequenceDesc(String draftId, String candidateIdentity);
    long countByDraftId(String draftId);
    SchemaDraftDecisionNode save(SchemaDraftDecisionNode decision);
}
