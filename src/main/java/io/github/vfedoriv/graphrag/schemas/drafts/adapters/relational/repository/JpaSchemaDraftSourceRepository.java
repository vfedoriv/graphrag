package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.repository;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceType;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftSourceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSchemaDraftSourceRepository extends JpaRepository<SchemaDraftSourceEntity, String> {
    List<SchemaDraftSourceEntity> findByDraftIdOrderByCreatedAtAscIdAsc(String draftId);
    List<SchemaDraftSourceEntity> findByDraftIdAndStatusOrderByCreatedAtAscIdAsc(
        String draftId, SchemaDraftSourceStatus status);
    boolean existsByDraftIdAndStatus(String draftId, SchemaDraftSourceStatus status);
    Optional<SchemaDraftSourceEntity> findByIdAndDraftId(String id, String draftId);
    Optional<SchemaDraftSourceEntity> findFirstByDraftIdAndTypeAndSha256AndStatusOrderByCreatedAtAscIdAsc(
        String draftId, SchemaDraftSourceType type, String sha256, SchemaDraftSourceStatus status);
    List<SchemaDraftSourceEntity> findByDraftIdInAndStatusOrderByDraftIdAscCreatedAtAscIdAsc(
        List<String> draftIds, SchemaDraftSourceStatus status);
}
