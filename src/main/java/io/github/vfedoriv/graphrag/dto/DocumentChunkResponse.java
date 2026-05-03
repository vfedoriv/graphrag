package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DocumentChunkResponse(
    @Schema(description = "Chunk identifier.", example = "chunk-01")
    String id,
    @Schema(description = "Parent document identifier.", example = "doc-01")
    String documentId,
    @Schema(description = "Zero-based chunk order index.", example = "0")
    int chunkIndex,
    @Schema(description = "Chunk text content.", example = "This agreement is made between...")
    String text,
    @Schema(description = "Estimated token count for this chunk.", example = "143")
    int tokenEstimate,
    @Schema(description = "Serialized chunk metadata.", example = "{\"source\":\"contract.pdf\",\"page\":2}")
    String metadata
) {
}
