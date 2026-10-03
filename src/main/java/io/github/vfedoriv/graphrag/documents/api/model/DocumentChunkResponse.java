package io.github.vfedoriv.graphrag.documents.api.model;

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
    @Schema(
        description = "Persisted chunk kind (PARENT or CHILD); virtual FLAT page requests still return CHILD.",
        example = "CHILD"
    )
    String kind,
    @Schema(description = "Containing parent identifier for child chunks.", example = "parent-01")
    String parentChunkId,
    @Schema(description = "Zero-based child order within the parent.", example = "0")
    Integer childIndex,
    @Schema(description = "Number of contained children for parent chunks.", example = "4")
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
