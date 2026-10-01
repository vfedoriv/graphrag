package io.github.vfedoriv.graphrag.documents.domain.options;

import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;

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
