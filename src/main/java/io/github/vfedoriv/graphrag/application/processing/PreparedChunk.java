package io.github.vfedoriv.graphrag.application.processing;

import java.util.Map;

public record PreparedChunk(String text, Map<String, Object> metadata) {
}
