package io.github.vfedoriv.graphrag.knowledgebase.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateKnowledgeBaseRequest(
    @Schema(description = "Knowledge base display name.", example = "Contracts KB")
    @NotBlank String name
) {
}
