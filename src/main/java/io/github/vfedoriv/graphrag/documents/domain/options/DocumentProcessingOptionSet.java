package io.github.vfedoriv.graphrag.documents.domain.options;

import java.util.Map;

public record DocumentProcessingOptionSet(
    DocumentFormatDetection detection,
    Map<String, Object> requestedOptions,
    Map<String, Object> savedDefaults,
    Map<String, Object> effectiveOptions
) {
}
