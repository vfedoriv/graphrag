package io.github.vfedoriv.graphrag.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateKnowledgeBaseAiProfileRequest(
    @NotBlank String profileId
) {
}
