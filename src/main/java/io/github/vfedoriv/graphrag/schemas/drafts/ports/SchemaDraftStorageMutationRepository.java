package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationState;
import java.time.Instant;
import java.util.List;

public interface SchemaDraftStorageMutationRepository {
    List<SchemaDraftStorageMutationNode> findByStateOrderByCreatedAtAsc(SchemaDraftStorageMutationState state);
    void deleteCompletedBefore(Instant before);
    java.util.Optional<SchemaDraftStorageMutationNode> findById(String id);
    SchemaDraftStorageMutationNode save(SchemaDraftStorageMutationNode mutation);
}
