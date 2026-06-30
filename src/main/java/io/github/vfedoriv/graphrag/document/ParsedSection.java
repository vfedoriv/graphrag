package io.github.vfedoriv.graphrag.document;

import java.util.Map;

public record ParsedSection(
    int sectionIndex,
    String text,
    String parserId,
    String format,
    Integer pageNumber,
    Integer pageCount,
    Map<String, Object> metadata
) {
    public ParsedSection {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
