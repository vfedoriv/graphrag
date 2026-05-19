package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record GenerateSchemaResponse(
    @Schema(
        description = "Generated schema in JSON format.",
        example = "{\"name\":\"generated-legal-schema\",\"version\":1,\"nodes\":[{\"label\":\"Party\",\"key\":\"id\"}],\"relationships\":[]}"
    )
    String content,
    @Schema(description = "Advisory warnings detected during generated schema checks. Non-blocking.")
    List<SchemaGenerationWarning> warnings
) {
    public GenerateSchemaResponse(String content) {
        this(content, List.of());
    }
}
