package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateSchemaRequest(
    @Schema(
        description = "Schema definition in JSON format.",
        example = "{\"name\":\"legal-contracts\",\"version\":1,\"nodes\":[{\"label\":\"Contract\",\"key\":\"contractId\"}],\"relationships\":[]}"
    )
    @NotBlank String content,
    @Schema(description = "Schema source classification.", example = "GENERATED")
    SchemaSourceType sourceType
) {
}
