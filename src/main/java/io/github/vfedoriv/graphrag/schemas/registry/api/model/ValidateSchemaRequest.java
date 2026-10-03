package io.github.vfedoriv.graphrag.schemas.registry.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ValidateSchemaRequest(
    @Schema(
        description = "Schema JSON content to validate.",
        example = "{\"name\":\"legal-contracts\",\"version\":1,\"nodes\":[{\"label\":\"Party\",\"key\":\"partyId\"}],\"relationships\":[]}"
    )
    @NotBlank String content
) {
}
