package io.github.vfedoriv.graphrag.documents.domain.options;

import java.util.List;

public record DocumentProcessingOptionConstraint(
    Number min,
    Number max,
    List<String> allowedValues
) {
    public static DocumentProcessingOptionConstraint none() {
        return new DocumentProcessingOptionConstraint(null, null, List.of());
    }

    public static DocumentProcessingOptionConstraint integerRange(int min, int max) {
        return new DocumentProcessingOptionConstraint(min, max, List.of());
    }

    public static DocumentProcessingOptionConstraint allowedValues(List<String> allowedValues) {
        return new DocumentProcessingOptionConstraint(null, null, List.copyOf(allowedValues));
    }
}
