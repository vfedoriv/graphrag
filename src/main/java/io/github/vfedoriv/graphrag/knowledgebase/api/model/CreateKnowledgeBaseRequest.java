package io.github.vfedoriv.graphrag.knowledgebase.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CreateKnowledgeBaseRequest(
    @Schema(description = "Knowledge base identifier.", example = "kb-demo")
    @NotBlank String id,
    @Schema(description = "Knowledge base display name.", example = "Demo knowledge base")
    @NotBlank String name
) {
}
