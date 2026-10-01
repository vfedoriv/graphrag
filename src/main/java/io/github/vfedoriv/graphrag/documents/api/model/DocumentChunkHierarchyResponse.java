package io.github.vfedoriv.graphrag.documents.api.model;

import io.github.vfedoriv.graphrag.dto.PageResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "DocumentChunkHierarchy", description = "Bounded metadata-only page of document hierarchy parents.")
public final class DocumentChunkHierarchyResponse extends PageResponse<DocumentChunkSummaryResponse> {

    private final long flatChunkCount;

    public DocumentChunkHierarchyResponse(
        int page,
        int size,
        long totalElements,
        List<DocumentChunkSummaryResponse> content,
        long flatChunkCount
    ) {
        super(page, size, totalElements, content);
        this.flatChunkCount = Math.max(0, flatChunkCount);
    }

    @Schema(description = "Number of non-parent chunks available for flat-document fallback paging.", example = "12")
    public long getFlatChunkCount() {
        return flatChunkCount;
    }
}
