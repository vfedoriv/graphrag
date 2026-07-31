package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HybridSearchExpandedContext(
    @Schema(description = "Persisted parent identifier, or the anchor child for legacy adjacency context.")
    String contextChunkId,
    @Schema(description = "Owning document identifier.")
    String documentId,
    @Schema(description = "Expansion kind: PARENT or ADJACENT.")
    String kind,
    @Schema(description = "Bounded context text.")
    String contextText,
    @Schema(description = "Combined source range for the expanded context.")
    HybridSearchSourceRange contextRange,
    @Schema(description = "Effective chunker revision used to validate expansion scope.")
    String strategyRevision,
    @Schema(description = "Ranked child evidence identifiers supported by this context.")
    List<String> evidenceChunkIds,
    @Schema(description = "Chunk identifiers whose text contributes to the context.")
    List<String> contributingChunkIds,
    @Schema(description = "Estimated context tokens.")
    int tokenEstimate,
    @Schema(description = "Expanded context is synthesis-only and is not text retrieval evidence.")
    HybridSearchCitationKind citationKind
) {
    public HybridSearchExpandedContext {
        evidenceChunkIds = evidenceChunkIds == null ? List.of() : List.copyOf(evidenceChunkIds);
        contributingChunkIds = contributingChunkIds == null ? List.of() : List.copyOf(contributingChunkIds);
        if (citationKind != HybridSearchCitationKind.CONTEXT_ONLY) {
            throw new IllegalArgumentException("Expanded text context must use CONTEXT_ONLY citations");
        }
    }
}
