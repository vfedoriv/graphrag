package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DocumentChunkSummary", description = "Metadata-only summary of a document hierarchy parent chunk.")
public record DocumentChunkSummaryResponse(
    @Schema(description = "Chunk identifier.", example = "parent-01")
    String id,
    @Schema(description = "Parent document identifier.", example = "doc-01")
    String documentId,
    @Schema(description = "Zero-based chunk order index.", example = "0")
    int chunkIndex,
    @Schema(description = "Estimated token count for this chunk.", example = "143")
    int tokenEstimate,
    @Schema(description = "Hierarchy chunk kind.", example = "PARENT")
    String kind,
    @Schema(description = "Containing parent identifier for child chunks.", example = "parent-01")
    String parentChunkId,
    @Schema(description = "Zero-based child order within the parent.", example = "0")
    Integer childIndex,
    @Schema(description = "Number of contained children for this parent.", example = "4")
    int childCount,
    String processingRunId,
    @Schema(description = "Parser section order.", example = "2")
    int sectionIndex,
    @Schema(description = "Chunk order within the parser section.", example = "0")
    int sectionChunkIndex,
    Integer sourceStart,
    Integer sourceEnd,
    Integer pageStart,
    Integer pageEnd,
    String structuralPath,
    String blockConfidence,
    String chunkSettingsHash,
    String chunkStrategyRevision,
    String effectiveChunkerRevision,
    String tokenizerId,
    String representationRevision,
    String sourceHash,
    @Schema(description = "Serialized chunk metadata.", example = "{\"source\":\"contract.pdf\",\"page\":2}")
    String metadata
) {
}
