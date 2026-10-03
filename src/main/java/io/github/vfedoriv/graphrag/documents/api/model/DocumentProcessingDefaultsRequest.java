package io.github.vfedoriv.graphrag.documents.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record DocumentProcessingDefaultsRequest(
    @Schema(description = "Document-scoped processing defaults to replace.")
    Map<String, Object> options
) {
}
