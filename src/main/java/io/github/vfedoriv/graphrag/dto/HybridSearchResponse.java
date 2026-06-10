package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HybridSearchResponse(
    @Schema(description = "Original search query.", example = "Acme renewal terms")
    String query,
    @Schema(description = "Applied chunk hit limit.", example = "10")
    int topK,
    @Schema(description = "Applied graph expansion depth.", example = "1")
    int graphDepth,
    @Schema(description = "Whether chunk text is included in each hit.", example = "true")
    boolean includeChunkText,
    @Schema(description = "Ranked hybrid search hits.")
    List<HybridSearchHit> hits,
    @Schema(description = "Number of hits returned.", example = "3")
    int hitCount,
    @Schema(description = "Search execution time in milliseconds.", example = "23")
    long executionTimeMs
) {
}
