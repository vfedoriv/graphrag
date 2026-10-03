package io.github.vfedoriv.graphrag.schemas.generation.domain;

import java.util.List;

public record SchemaGenerationResult(
    String content,
    List<SchemaGenerationWarning> warnings
) {
}
