package io.github.vfedoriv.graphrag.documents.domain.parsing;

public record SourceRange(int startInclusive, int endExclusive) {

    public SourceRange {
        if (startInclusive < 0) {
            throw new IllegalArgumentException("Source range start must not be negative");
        }
        if (endExclusive < startInclusive) {
            throw new IllegalArgumentException("Source range end must not precede its start");
        }
    }

    public int length() {
        return endExclusive - startInclusive;
    }
}
