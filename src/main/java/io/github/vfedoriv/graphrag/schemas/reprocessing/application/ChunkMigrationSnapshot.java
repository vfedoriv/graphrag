package io.github.vfedoriv.graphrag.schemas.reprocessing.application;

import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.ChunkReprocessingSelection;
import java.util.Map;

public record ChunkMigrationSnapshot(
    String targetRevision,
    ChunkReprocessingSelection selection,
    ChunkTarget chunkTarget,
    String aiProfileId,
    long aiProfileRevision,
    String embeddingSpaceId,
    String schemaId,
    String schemaContentHash,
    Map<String, DocumentTarget> documents
) {
    public ChunkMigrationSnapshot {
        documents = Map.copyOf(documents);
    }

    public record ChunkTarget(
        String strategyName,
        String strategyRevision,
        int targetTokens,
        int overlapTokens,
        int hardCharacterLimit,
        int parentTargetTokens,
        int parentHardCharacterLimit,
        int parentMaxPages,
        int contextHeaderMaxTokens,
        int contextHeaderMaxCharacters,
        String tokenizerId,
        String tokenizerRevision,
        String tokenCountMode,
        String representationRevision,
        String settingsHash
    ) {
    }

    public record DocumentTarget(
        String sourceSha256,
        String parserId,
        String parserRevision,
        String fileFormat,
        String effectiveChunkerRevision,
        Map<String, Object> effectiveProcessingOptions
    ) {
        public DocumentTarget {
            effectiveProcessingOptions = Map.copyOf(effectiveProcessingOptions);
        }
    }
}
