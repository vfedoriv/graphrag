package io.github.vfedoriv.graphrag.document.chunking;

import java.util.Map;
import java.util.List;

public record ChunkSlice(
    String text,
    Integer sourceStart,
    Integer sourceEnd,
    int tokenCount,
    String strategyName,
    String strategyRevision,
    TokenizerId tokenizerId,
    TokenCountMode countMode,
    ChunkSettingsHash settingsHash,
    ChunkerRevision effectiveRevision,
    String kind,
    int sectionIndex,
    int sectionChunkIndex,
    Integer pageStart,
    Integer pageEnd,
    List<String> structuralPath,
    String blockConfidence,
    String sourceHash,
    Map<String, Object> diagnostics
) {

    public ChunkSlice(
        String text,
        Integer sourceStart,
        Integer sourceEnd,
        int tokenCount,
        String strategyName,
        String strategyRevision,
        TokenizerId tokenizerId,
        TokenCountMode countMode,
        ChunkSettingsHash settingsHash,
        ChunkerRevision effectiveRevision,
        Map<String, Object> diagnostics
    ) {
        this(
            text,
            sourceStart,
            sourceEnd,
            tokenCount,
            strategyName,
            strategyRevision,
            tokenizerId,
            countMode,
            settingsHash,
            effectiveRevision,
            "CHILD",
            0,
            0,
            null,
            null,
            List.of(),
            null,
            null,
            diagnostics
        );
    }

    public ChunkSlice {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Chunk text must not be empty");
        }
        if ((sourceStart == null) != (sourceEnd == null)) {
            throw new IllegalArgumentException("Source positions must both be present or absent");
        }
        if (sourceStart != null && (sourceStart < 0 || sourceEnd < sourceStart)) {
            throw new IllegalArgumentException("Source positions are invalid");
        }
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must be non-negative");
        }
        kind = kind == null || kind.isBlank() ? "CHILD" : kind.strip();
        if (sectionIndex < 0 || sectionChunkIndex < 0) {
            throw new IllegalArgumentException("Chunk section indexes must not be negative");
        }
        if ((pageStart == null) != (pageEnd == null) || (pageStart != null && pageEnd < pageStart)) {
            throw new IllegalArgumentException("Chunk page range is invalid");
        }
        structuralPath = structuralPath == null ? List.of() : List.copyOf(structuralPath);
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
        if (diagnostics.size() > 16) {
            throw new IllegalArgumentException("Chunk diagnostics must contain at most 16 entries");
        }
    }
}
