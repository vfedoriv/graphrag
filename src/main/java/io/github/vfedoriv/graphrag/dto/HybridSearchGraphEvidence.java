package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HybridSearchGraphEvidence(
    @Schema(description = "Graph extraction evidence identifier.")
    String evidenceId,
    @Schema(description = "Graph fact kind.")
    String factKind,
    @Schema(description = "Canonical graph fact identifier.")
    String canonicalFactId,
    @Schema(description = "Source document identifier.")
    String sourceDocumentId,
    @Schema(description = "Authoritative extraction parent identifier.")
    String sourceChunkId,
    @Schema(description = "Permitted authoritative parent text, omitted when text was not requested.")
    String sourceText,
    @Schema(description = "Authoritative extraction parent range.")
    HybridSearchSourceRange sourceRange,
    @Schema(description = "Processing run associated with the graph evidence.")
    String processingRunId,
    @Schema(description = "Effective chunker revision associated with the graph evidence.")
    String strategyRevision,
    @Schema(description = "Citation kind for graph-derived claims.")
    HybridSearchCitationKind citationKind
) {
    public HybridSearchGraphEvidence {
        if (citationKind != HybridSearchCitationKind.GRAPH_PARENT) {
            throw new IllegalArgumentException("Graph evidence must use GRAPH_PARENT citations");
        }
    }
}
