package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record GenerateSchemaResponse(
    @Schema(
        description = "Generated schema in JSON format.",
        example = "{\"name\":\"generated-legal-schema\",\"version\":1,\"nodes\":[{\"label\":\"Party\",\"key\":\"id\"}],\"relationships\":[]}"
    )
    String content
) {
}
