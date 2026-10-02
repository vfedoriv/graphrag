package io.github.vfedoriv.graphrag.schemas.discovery;

import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.SourceType;
import java.util.List;

public record PreparedDiscoverySource(
    String sourceId,
    SourceType type,
    String name,
    String documentId,
    String fingerprint,
    List<AnalysisChunk> chunks
) {
    public PreparedDiscoverySource {
        chunks = List.copyOf(chunks);
    }

    public record AnalysisChunk(String id, int ordinal, String text, String fingerprint) {
    }
}
