package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record HybridSearchRequest(
    @Schema(description = "Natural-language text to embed and search for.", example = "Acme renewal terms")
    @NotBlank String query,
    @Schema(description = "Maximum number of ranked chunk hits to return.", example = "10")
    @Min(1) Integer topK,
    @Schema(description = "Depth for bounded graph expansion from mentioned entities.", example = "1")
    @Min(0) Integer graphDepth,
    @Schema(description = "Whether to include chunk text in the response.", example = "true")
    Boolean includeChunkText
) {
}
