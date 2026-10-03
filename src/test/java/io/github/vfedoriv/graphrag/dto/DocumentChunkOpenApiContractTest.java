package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.documents.api.model.DocumentChunkHierarchyResponse;
import io.github.vfedoriv.graphrag.documents.api.model.DocumentChunkPageResponse;
import io.github.vfedoriv.graphrag.documents.api.model.DocumentChunkSummaryResponse;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentChunkOpenApiContractTest {

    @Test
    void exposesTypedChunkPageAndHierarchySchemas() {
        assertPageSchema("DocumentChunkPage", DocumentChunkPageResponse.class);
        assertPageSchema("DocumentChunkHierarchy", DocumentChunkHierarchyResponse.class);

        Map<String, Schema> hierarchySchemas = ModelConverters.getInstance()
            .readAll(DocumentChunkHierarchyResponse.class);
        assertThat(hierarchySchemas.get("DocumentChunkHierarchy").getProperties())
            .containsKeys("page", "size", "totalElements", "content", "flatChunkCount");
    }

    @Test
    void hierarchySummaryDoesNotExposeChunkText() {
        Map<String, Schema> schemas = ModelConverters.getInstance().readAll(DocumentChunkSummaryResponse.class);

        assertThat(schemas.get("DocumentChunkSummary").getProperties())
            .containsKeys("id", "chunkIndex", "childCount", "sourceStart", "sourceHash", "metadata")
            .doesNotContainKey("text");
    }

    private void assertPageSchema(String expectedName, Class<?> type) {
        Map<String, Schema> schemas = ModelConverters.getInstance().readAll(type);
        assertThat(schemas).containsKey(expectedName);
        assertThat(schemas.get(expectedName).getProperties())
            .containsKeys("page", "size", "totalElements", "content");
    }
}
