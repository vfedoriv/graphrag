package io.github.vfedoriv.graphrag.schemas.registry.api.model;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateSchemaRequest(
    @Schema(
        description = "Schema definition in JSON format.",
        example = "{\"name\":\"legal-contracts\",\"version\":1,\"nodes\":[{\"label\":\"Contract\",\"key\":\"contractId\"}],\"relationships\":[]}"
    )
    @NotBlank String content,
    @Schema(description = "Schema source classification.", example = "GENERATED")
    SchemaSourceType sourceType,
    @Schema(description = "Optional knowledge base identifier to associate the created schema with.", example = "kb-demo")
    String knowledgeBaseId
) {
}
