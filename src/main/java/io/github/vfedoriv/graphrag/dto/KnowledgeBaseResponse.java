package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record KnowledgeBaseResponse(
    @Schema(description = "Knowledge base identifier.", example = "kb-demo")
    String id,
    @Schema(description = "Knowledge base display name.", example = "Demo knowledge base")
    String name,
    @Schema(description = "Currently active schema identifier.", example = "schema-01")
    String activeSchemaId,
    @Schema(description = "Creation timestamp in UTC.", example = "2026-05-03T10:12:00Z")
    Instant createdAt
) {
}
