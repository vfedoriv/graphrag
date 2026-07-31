package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.document.chunking.ChunkingContext;

public record ImmutableDocumentProcessingInput(
    String aiProfileId,
    long aiProfileRevision,
    String embeddingSpaceId,
    String schemaId,
    String schemaContentHash,
    DocumentProcessingOptionSet processingOptions,
    ChunkingContext chunkingContext
) {
}
