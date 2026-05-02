package io.github.vfedoriv.graphrag.dto;

public record DocumentChunkResponse(
    String id,
    String documentId,
    int chunkIndex,
    String text,
    int tokenEstimate,
    String metadata
) {
}
