package io.github.vfedoriv.graphrag;

import org.springframework.jdbc.core.JdbcTemplate;

final class RelationalMetadataTestCleaner {
    private RelationalMetadataTestCleaner() {}

    static void clean(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("UPDATE app.schema_draft SET running_analysis_run_id = NULL, current_aggregate_id = NULL");
        jdbcTemplate.update("UPDATE app.schema_draft_analysis_run SET aggregate_revision_id = NULL");
        jdbcTemplate.update("DELETE FROM app.schema_reprocessing_item");
        jdbcTemplate.update("DELETE FROM app.schema_reprocessing_plan");
        jdbcTemplate.update("DELETE FROM app.schema_draft_publication");
        jdbcTemplate.update("DELETE FROM app.schema_draft_evaluation_outcome");
        jdbcTemplate.update("DELETE FROM app.schema_draft_evaluation_run");
        jdbcTemplate.update("DELETE FROM app.schema_draft_storage_mutation");
        jdbcTemplate.update("DELETE FROM app.schema_draft_decision");
        jdbcTemplate.update("DELETE FROM app.schema_draft_conflict");
        jdbcTemplate.update("DELETE FROM app.schema_draft_source_result");
        jdbcTemplate.update("DELETE FROM app.schema_draft_aggregate_revision");
        jdbcTemplate.update("DELETE FROM app.schema_draft_analysis_run");
        jdbcTemplate.update("DELETE FROM app.schema_draft_source_revision");
        jdbcTemplate.update("DELETE FROM app.schema_draft_source");
        jdbcTemplate.update("DELETE FROM app.schema_draft");
        jdbcTemplate.update("DELETE FROM app.document_storage_mutation");
        jdbcTemplate.update("DELETE FROM app.extraction_run");
        jdbcTemplate.update("DELETE FROM app.document_processing_run");
        jdbcTemplate.update("DELETE FROM app.document_upload");
        jdbcTemplate.update("DELETE FROM app.knowledge_base_schema");
        jdbcTemplate.update("DELETE FROM app.knowledge_base");
        jdbcTemplate.update("DELETE FROM app.schema_definition");
    }
}
