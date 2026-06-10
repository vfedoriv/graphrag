package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HybridSearchHit(
    @Schema(description = "Matched chunk identifier.")
    String chunkId,
    @Schema(description = "Owning document identifier.")
    String documentId,
    @Schema(description = "Chunk index within the document.", example = "0")
    int chunkIndex,
    @Schema(description = "Vector similarity score.", example = "0.93")
    double score,
    @Schema(description = "Chunk text, omitted when not requested.")
    String text,
    @Schema(description = "Document and chunk source metadata.")
    HybridSearchSource source,
    @Schema(description = "Graph context expanded from the matched chunk.")
    HybridSearchGraphContext graph
) {
}
