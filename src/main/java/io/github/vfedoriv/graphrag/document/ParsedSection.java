package io.github.vfedoriv.graphrag.document;

import java.util.List;
import java.util.Map;

public record ParsedSection(
    int sectionIndex,
    String text,
    String parserId,
    String format,
    Integer pageNumber,
    Integer pageCount,
    Map<String, Object> metadata,
    List<ParsedBlock> blocks
) {

    public ParsedSection(
        int sectionIndex,
        String text,
        String parserId,
        String format,
        Integer pageNumber,
        Integer pageCount,
        Map<String, Object> metadata
    ) {
        this(sectionIndex, text, parserId, format, pageNumber, pageCount, metadata, List.of());
    }

    public ParsedSection {
        if (sectionIndex < 0) {
            throw new IllegalArgumentException("Section index must not be negative");
        }
        text = text == null ? "" : text;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        int previousStart = -1;
        for (ParsedBlock block : blocks) {
            SourceRange range = block.sourceRange();
            if (range.endExclusive() > text.length()) {
                throw new IllegalArgumentException("Parsed block range exceeds section text");
            }
            if (!text.substring(range.startInclusive(), range.endExclusive()).equals(block.text())) {
                throw new IllegalArgumentException("Parsed block text must match its section source range");
            }
            if (range.startInclusive() < previousStart) {
                throw new IllegalArgumentException("Parsed blocks must be ordered by source range");
            }
            previousStart = range.startInclusive();
        }
    }
}
