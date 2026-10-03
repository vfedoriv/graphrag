package io.github.vfedoriv.graphrag.documents.domain.parsing;

import java.util.Arrays;
import java.util.List;

public record StructuralPath(List<String> segments) {

    public StructuralPath {
        segments = segments == null ? List.of() : List.copyOf(segments);
        if (segments.stream().anyMatch(segment -> segment == null || segment.isBlank())) {
            throw new IllegalArgumentException("Structural path segments must not be blank");
        }
    }

    public static StructuralPath of(String... segments) {
        return new StructuralPath(segments == null ? List.of() : Arrays.asList(segments));
    }

    public StructuralPath append(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("Structural path segment must not be blank");
        }
        java.util.ArrayList<String> extended = new java.util.ArrayList<>(segments);
        extended.add(segment);
        return new StructuralPath(extended);
    }
}
