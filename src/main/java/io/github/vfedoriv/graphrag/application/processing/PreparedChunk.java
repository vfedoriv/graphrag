package io.github.vfedoriv.graphrag.application.processing;

import io.github.vfedoriv.graphrag.document.chunking.ChunkSlice;
import java.util.Map;

public record PreparedChunk(String text, int tokenCount, ChunkSlice slice, Map<String, Object> metadata) {

    public PreparedChunk(String text, Map<String, Object> metadata) {
        this(text, text == null || text.isBlank() ? 0 : (int) Math.ceil(text.length() / 4.0), null, metadata);
    }
}
