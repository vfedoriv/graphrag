package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "DocumentChunkPage", description = "Zero-based bounded page of document chunks.")
public final class DocumentChunkPageResponse extends PageResponse<DocumentChunkResponse> {

    public DocumentChunkPageResponse(int page, int size, long totalElements, List<DocumentChunkResponse> content) {
        super(page, size, totalElements, content);
    }
}
