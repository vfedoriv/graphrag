package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HybridSearchSource(
    @Schema(description = "Document identifier.")
    String documentId,
    @Schema(description = "Original uploaded filename.")
    String originalFilename,
    @Schema(description = "Uploaded content type.")
    String contentType,
    @Schema(description = "Uploaded size in bytes.")
    long sizeBytes,
    @Schema(description = "Chunk metadata persisted during document processing.")
    String chunkMetadata
) {
}
