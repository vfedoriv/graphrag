package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationState;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftStorageMutationEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSchemaDraftStorageMutationRepository
    extends JpaRepository<SchemaDraftStorageMutationEntity, String> {
    List<SchemaDraftStorageMutationEntity> findByStateOrderByCreatedAtAscIdAsc(
        SchemaDraftStorageMutationState state);
    long deleteByStateAndCompletedAtBefore(SchemaDraftStorageMutationState state, Instant before);
}
