package io.github.vfedoriv.graphrag.document;

public record ParserRevision(String value) {

    public ParserRevision {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Parser revision must not be blank");
        }
    }
}
