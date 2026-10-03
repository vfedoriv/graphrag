package io.github.vfedoriv.graphrag.schemas.generation.domain;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SchemaGenerationNormalizationSupport {

    private SchemaGenerationNormalizationSupport() {
    }

    public static String sanitizeLabel(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isBlank()) {
            return fallback;
        }
        String first = cleaned.substring(0, 1).toUpperCase(Locale.ROOT);
        return first + cleaned.substring(1);
    }

    public static String sanitizeRelation(String raw) {
        if (raw == null || raw.isBlank()) {
            return "RELATED_TO";
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "RELATED_TO" : cleaned;
    }

    public static String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return (right == null || right.isBlank()) ? null : right;
    }

    public static String inferPropertyType(String value) {
        if (value == null || value.isBlank()) {
            return "string";
        }
        String normalized = value.trim();
        if (normalized.matches("(?i)true|false")) {
            return "boolean";
        }
        if (normalized.matches("[+-]?\\d+")) {
            return "integer";
        }
        if (normalized.matches("[+-]?\\d*\\.\\d+")) {
            return "number";
        }
        if (normalized.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "date";
        }
        if (normalized.matches("\\d{4}-\\d{2}-\\d{2}[Tt ][0-2]\\d:[0-5]\\d:[0-5]\\d(?:\\.\\d{1,9})?(?:[Zz]|[+-][0-2]\\d:[0-5]\\d)?")) {
            return "datetime";
        }
        return "string";
    }

    public static List<String> inferKeyCandidates(List<SchemaDocument.PropertyDefinition> properties) {
        if (properties == null || properties.isEmpty()) {
            return List.of();
        }
        List<String> names = properties.stream()
            .map(SchemaDocument.PropertyDefinition::name)
            .filter(Objects::nonNull)
            .toList();
        List<String> preferred = names.stream()
            .filter(name -> name.equals("id") || name.endsWith("Id") || name.endsWith("Code") || name.endsWith("Number"))
            .toList();
        if (!preferred.isEmpty()) {
            return preferred.size() == 1 ? List.of(preferred.getFirst()) : preferred.subList(0, Math.min(preferred.size(), 2));
        }
        return names.size() == 1 ? List.of(names.getFirst()) : names.subList(0, 2);
    }

    public static boolean isSafePropertyName(String value) {
        return value.matches("[A-Za-z_][A-Za-z0-9_]*");
    }
}
