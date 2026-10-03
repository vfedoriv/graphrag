package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.repository;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceResultStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftSourceResultEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSchemaDraftSourceResultRepository
    extends JpaRepository<SchemaDraftSourceResultEntity, String> {
    Optional<SchemaDraftSourceResultEntity> findFirstByDraftIdAndReuseKeyAndStatusOrderByCompletedAtDescIdDesc(
        String draftId, String reuseKey, SchemaDraftSourceResultStatus status);
    List<SchemaDraftSourceResultEntity> findByRunIdOrderByCreatedAtAscIdAsc(String runId);
    Page<SchemaDraftSourceResultEntity> findByRunId(String runId, Pageable pageable);

    @Query(value = """
        SELECT DISTINCT result.source_sha256
        FROM app.schema_draft draft
        JOIN app.schema_draft_aggregate_revision aggregate_revision
          ON aggregate_revision.id = draft.current_aggregate_id
         AND aggregate_revision.draft_id = draft.id
        JOIN app.schema_draft_source_result result
          ON result.run_id = aggregate_revision.run_id
         AND result.status = 'SUCCEEDED'
        WHERE draft.id = :draftId AND btrim(coalesce(result.source_sha256, '')) <> ''
        """, nativeQuery = true)
    List<String> findContributingSourceSha256s(@Param("draftId") String draftId);

    @Query(value = """
        SELECT DISTINCT revision.document_id
        FROM app.schema_draft draft
        JOIN app.schema_draft_aggregate_revision aggregate_revision
          ON aggregate_revision.id = draft.current_aggregate_id
         AND aggregate_revision.draft_id = draft.id
        JOIN app.schema_draft_source_result result
          ON result.run_id = aggregate_revision.run_id
         AND result.status = 'SUCCEEDED'
        JOIN app.schema_draft_source_revision revision
          ON revision.draft_id = draft.id
         AND revision.source_id = result.source_id
         AND revision.revision = result.source_revision
        WHERE draft.id = :draftId
          AND btrim(coalesce(result.source_sha256, '')) = ''
          AND revision.document_id IS NOT NULL
        """, nativeQuery = true)
    List<String> findHistoricalContributingDocumentIds(@Param("draftId") String draftId);
}
