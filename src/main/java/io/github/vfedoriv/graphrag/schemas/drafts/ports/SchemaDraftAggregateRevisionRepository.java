package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAggregateRevisionNode;
import java.util.List;
import java.util.Optional;

public interface SchemaDraftAggregateRevisionRepository {
    List<SchemaDraftAggregateRevisionNode> findByDraftIdOrderByRevisionDesc(String draftId);
    Optional<SchemaDraftAggregateRevisionNode> findFirstByDraftIdOrderByRevisionDesc(String draftId);

    Optional<SchemaDraftAggregateRevisionNode> findLegacyPreviousPromoted(String draftId, String aggregateId);
    Optional<SchemaDraftAggregateRevisionNode> findById(String id);
    SchemaDraftAggregateRevisionNode save(SchemaDraftAggregateRevisionNode aggregate);
}
