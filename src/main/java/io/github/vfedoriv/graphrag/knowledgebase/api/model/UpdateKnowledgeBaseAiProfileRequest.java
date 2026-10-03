package io.github.vfedoriv.graphrag.knowledgebase.api.model;

import jakarta.validation.constraints.NotBlank;

public record UpdateKnowledgeBaseAiProfileRequest(
    @NotBlank String profileId
) {
}
