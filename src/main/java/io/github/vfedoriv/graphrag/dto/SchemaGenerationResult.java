package io.github.vfedoriv.graphrag.dto;

import java.util.List;

public record SchemaGenerationResult(
    String content,
    List<SchemaGenerationWarning> warnings
) {
}
