package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictNode;
import java.util.List;
import java.util.Optional;

public interface SchemaDraftConflictRepository {
    Optional<SchemaDraftConflictNode> findByIdAndDraftId(String id, String draftId);

    List<SchemaDraftConflictNode> findByAggregateRevision(
        String draftId, String aggregateRevisionId
    );
    List<SchemaDraftConflictNode> findHistory(String draftId);
    List<SchemaDraftConflictNode> findResolvedHistory(
        String draftId, String aggregateRevisionId
    );
    SchemaDraftConflictNode save(SchemaDraftConflictNode conflict);
}
