package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.domain.SchemaFormat;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record SchemaResponse(
    @Schema(description = "Schema identifier.", example = "schema-01")
    String id,
    @Schema(description = "Logical schema name.", example = "legal-contracts")
    String name,
    @Schema(description = "Schema version number.", example = "1")
    int version,
    @Schema(description = "Schema source classification.", example = "USER_DEFINED")
    SchemaSourceType sourceType,
    @Schema(description = "Schema document format.", example = "YAML")
    SchemaFormat format,
    @Schema(description = "Hash of schema content.", example = "a74f9f7fbb...")
    String contentHash,
    @Schema(description = "Schema lifecycle status.", example = "ACTIVE")
    SchemaStatus status,
    @Schema(description = "Creation timestamp in UTC.", example = "2026-05-03T10:12:00Z")
    Instant createdAt
) {
}
