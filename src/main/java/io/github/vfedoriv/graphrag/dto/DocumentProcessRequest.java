package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record DocumentProcessRequest(
    @Schema(description = "Allow replacing an existing completed extraction for this document.")
    Boolean allowOverwrite,
    @Schema(description = "One-run processing option overrides.")
    Map<String, Object> options
) {
}
