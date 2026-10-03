package io.github.vfedoriv.graphrag.documents.domain.chunking;

public record ChunkerRevision(String value) {

    public ChunkerRevision {
        if (value == null || !value.matches("chunker_[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Chunker revision must be a chunker_ prefixed SHA-256 value");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
