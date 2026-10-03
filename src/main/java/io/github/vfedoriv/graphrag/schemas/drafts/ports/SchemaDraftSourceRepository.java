package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceType;
import java.util.List;
import java.util.Optional;

public interface SchemaDraftSourceRepository {
    List<SchemaDraftSourceNode> findByDraftIdOrderByCreatedAtAsc(String draftId);
    List<SchemaDraftSourceNode> findByDraftIdAndStatusOrderByCreatedAtAsc(String draftId, SchemaDraftSourceStatus status);
    boolean existsByDraftIdAndStatus(String draftId, SchemaDraftSourceStatus status);
    Optional<SchemaDraftSourceNode> findByIdAndDraftId(String id, String draftId);
    Optional<SchemaDraftSourceNode> findFirstByDraftIdAndTypeAndSha256AndStatus(
        String draftId, SchemaDraftSourceType type, String sha256, SchemaDraftSourceStatus status
    );

    List<SchemaDraftSourceNode> findActiveForDraftIds(List<String> draftIds);
    Optional<SchemaDraftSourceNode> findById(String id);
    SchemaDraftSourceNode save(SchemaDraftSourceNode source);
    void delete(SchemaDraftSourceNode source);
}
