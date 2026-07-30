package io.github.vfedoriv.graphrag.document;

import java.util.Map;
import java.util.Objects;

public record ParsedBlock(
    ParsedBlockKind kind,
    String text,
    SourceRange sourceRange,
    StructuralPath structuralPath,
    ParsedBlockConfidence confidence,
    ParserRevision parserRevision,
    Map<String, Object> metadata
) {

    public ParsedBlock {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(sourceRange, "sourceRange");
        Objects.requireNonNull(structuralPath, "structuralPath");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(parserRevision, "parserRevision");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (sourceRange.length() != text.length()) {
            throw new IllegalArgumentException("Parsed block text length must match its source range");
        }
    }
}
