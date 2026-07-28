package io.github.vfedoriv.graphrag;

import org.springframework.jdbc.core.JdbcTemplate;

final class RelationalMetadataTestCleaner {
    private RelationalMetadataTestCleaner() {}

    static void clean(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM app.document_storage_mutation");
        jdbcTemplate.update("DELETE FROM app.extraction_run");
        jdbcTemplate.update("DELETE FROM app.document_processing_run");
        jdbcTemplate.update("DELETE FROM app.document_upload");
        jdbcTemplate.update("DELETE FROM app.knowledge_base_schema");
        jdbcTemplate.update("DELETE FROM app.knowledge_base");
        jdbcTemplate.update("DELETE FROM app.schema_definition");
    }
}
