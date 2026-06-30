package io.github.vfedoriv.graphrag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DocumentProcessingOptionsResponse(
    @Schema(description = "Document identifier.")
    String documentId,
    @Schema(description = "Detected parser identifier.", example = "tika")
    String parserId,
    @Schema(description = "Detected file format.", example = "PDF")
    String fileFormat,
    @Schema(description = "Current saved document defaults.")
    Map<String, Object> savedDefaults,
    @Schema(description = "Last saved defaults update timestamp.")
    Instant savedDefaultsUpdatedAt,
    @Schema(description = "Applicable option definitions.")
    List<DocumentProcessingOptionResponse> options
) {
}
