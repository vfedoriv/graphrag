package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HybridSearchRetrievalEvidence(
    @Schema(description = "Authoritative retrieved child chunk identifier.")
    String retrievalChunkId,
    @Schema(description = "Authoritative child source text, omitted when chunk text was not requested.")
    String sourceText,
    @Schema(description = "Precise child source range.")
    HybridSearchSourceRange sourceSpan,
    @Schema(description = "Retained processing run identifier.")
    String processingRunId,
    @Schema(description = "Effective chunker revision.")
    String strategyRevision,
    @Schema(description = "Structural path used to constrain adjacency.")
    String structuralPath,
    @Schema(description = "Citation kind for text retrieval evidence.")
    HybridSearchCitationKind citationKind
) {
    public HybridSearchRetrievalEvidence {
        if (citationKind != HybridSearchCitationKind.TEXT_CHILD) {
            throw new IllegalArgumentException("Retrieval evidence must use TEXT_CHILD citations");
        }
    }
}
