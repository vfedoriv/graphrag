package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftDecisionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSchemaDraftDecisionRepository extends JpaRepository<SchemaDraftDecisionEntity, String> {
    List<SchemaDraftDecisionEntity> findByDraftIdOrderBySequenceAsc(String draftId);
    List<SchemaDraftDecisionEntity> findByDraftIdAndCandidateIdentityOrderBySequenceDesc(
        String draftId, String candidateIdentity);
    long countByDraftId(String draftId);
}
