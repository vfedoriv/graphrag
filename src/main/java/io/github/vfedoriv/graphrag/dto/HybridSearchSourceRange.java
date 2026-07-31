package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HybridSearchSourceRange(
    @Schema(description = "Inclusive source offset when available.")
    Integer sourceStart,
    @Schema(description = "Exclusive source offset when available.")
    Integer sourceEnd,
    @Schema(description = "First source page when available.")
    Integer pageStart,
    @Schema(description = "Last source page when available.")
    Integer pageEnd
) {
}
