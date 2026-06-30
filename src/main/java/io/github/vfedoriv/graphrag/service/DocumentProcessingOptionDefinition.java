package io.github.vfedoriv.graphrag.service;

import java.util.List;

public record DocumentProcessingOptionDefinition(
    String key,
    String label,
    DocumentProcessingOptionValueType valueType,
    Object defaultValue,
    DocumentProcessingOptionConstraint constraints,
    List<String> parserIds,
    List<String> fileFormats,
    boolean mutable,
    String description
) {
    boolean appliesTo(DocumentFormatDetection detection) {
        return parserIds.contains(detection.parserId()) && fileFormats.contains(detection.fileFormat());
    }
}
