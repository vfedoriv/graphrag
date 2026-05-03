package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record QueryGenerateRequest(
    @Schema(description = "Natural language question or request to translate into Cypher.", example = "Show top 10 contracts signed in 2025")
    @NotBlank String prompt
) {
}
