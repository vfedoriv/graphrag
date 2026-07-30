package io.github.vfedoriv.graphrag.document.chunking;

import java.util.Map;

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
    Map<String, Object> diagnostics
) {

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
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
        if (diagnostics.size() > 16) {
            throw new IllegalArgumentException("Chunk diagnostics must contain at most 16 entries");
        }
    }
}
