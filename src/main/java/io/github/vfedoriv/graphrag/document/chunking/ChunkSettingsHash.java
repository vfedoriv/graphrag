package io.github.vfedoriv.graphrag.document.chunking;

public record ChunkSettingsHash(String value) {

    public ChunkSettingsHash {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Chunk settings hash must be a lowercase SHA-256 value");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
