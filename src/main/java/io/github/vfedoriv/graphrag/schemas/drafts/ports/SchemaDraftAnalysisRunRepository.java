package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchemaDraftAnalysisRunRepository {
    Optional<SchemaDraftAnalysisRunNode> findFirstByDraftIdAndStatusOrderByCreatedAtDesc(
        String draftId, SchemaDraftAnalysisStatus status
    );
    List<SchemaDraftAnalysisRunNode> findByStatus(SchemaDraftAnalysisStatus status);
    List<SchemaDraftAnalysisRunNode> findByDraftIdOrderByCreatedAtDesc(String draftId);
    Optional<SchemaDraftAnalysisRunNode> findByIdAndDraftId(String id, String draftId);

    Page<SchemaDraftAnalysisRunNode> findPageByDraftId(String draftId, Pageable pageable);
    List<SchemaDraftAnalysisRunNode> findCurrentForDraftIds(List<String> draftIds);
    Long claim(String runId, String workerId, Instant claimedAt);
    Optional<SchemaDraftAnalysisRunNode> findById(String id);
    SchemaDraftAnalysisRunNode save(SchemaDraftAnalysisRunNode run);
    void deleteById(String id);
}
