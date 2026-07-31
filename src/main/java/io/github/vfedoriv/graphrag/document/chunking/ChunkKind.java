package io.github.vfedoriv.graphrag.document.chunking;

public enum ChunkKind {
    PARENT,
    CHILD;

    public static ChunkKind from(String value) {
        if (value == null || value.isBlank()) {
            return CHILD;
        }
        return ChunkKind.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
    }
}
