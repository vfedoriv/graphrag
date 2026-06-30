package io.github.vfedoriv.graphrag.service;

import java.util.Map;

public record DocumentProcessingOptionSet(
    DocumentFormatDetection detection,
    Map<String, Object> requestedOptions,
    Map<String, Object> savedDefaults,
    Map<String, Object> effectiveOptions
) {
}
