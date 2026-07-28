package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationState;
import java.time.Instant;
import java.util.List;

public interface SchemaDraftStorageMutationRepository {
    List<SchemaDraftStorageMutationNode> findByStateOrderByCreatedAtAsc(SchemaDraftStorageMutationState state);
    void deleteCompletedBefore(Instant before);
    java.util.Optional<SchemaDraftStorageMutationNode> findById(String id);
    SchemaDraftStorageMutationNode save(SchemaDraftStorageMutationNode mutation);
}
