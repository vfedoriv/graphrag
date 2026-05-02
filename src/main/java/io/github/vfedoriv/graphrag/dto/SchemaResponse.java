package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.domain.SchemaFormat;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import java.time.Instant;

public record SchemaResponse(
    String id,
    String name,
    int version,
    SchemaSourceType sourceType,
    SchemaFormat format,
    String contentHash,
    SchemaStatus status,
    Instant createdAt
) {
}
