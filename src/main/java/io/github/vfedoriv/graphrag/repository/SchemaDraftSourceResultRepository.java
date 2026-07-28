package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchemaDraftSourceResultRepository {
    Optional<SchemaDraftSourceResultNode> findFirstByDraftIdAndReuseKeyAndStatusOrderByCompletedAtDesc(
        String draftId, String reuseKey, SchemaDraftSourceResultStatus status
    );
    List<SchemaDraftSourceResultNode> findByRunIdOrderByCreatedAtAsc(String runId);

    Page<SchemaDraftSourceResultNode> findPageByRunId(String runId, Pageable pageable);
    List<String> findContributingSourceSha256s(String draftId);
    List<String> findHistoricalContributingDocumentIds(String draftId);
    SchemaDraftSourceResultNode save(SchemaDraftSourceResultNode result);
}
