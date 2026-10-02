package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.Metrics;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.Rate;
import io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SchemaDraftEvaluationMetricsCalculator {
    public Metrics calculate(
        GraphExtractionResult raw, GraphExtractionResult sanitized, SchemaDocument schema,
        long lowSupportCandidates, long guidedWithoutEvidenceCandidates
    ) {
        Map<String, SchemaDocument.NodeDefinition> nodes = safe(schema.nodes()).stream()
            .collect(Collectors.toMap(SchemaDocument.NodeDefinition::label, Function.identity()));
        Set<String> relationships = safe(schema.relationships()).stream()
            .map(value -> relationshipKey(value.type(), value.from(), value.to())).collect(Collectors.toSet());
        long recognized = raw.nodes().stream().filter(value -> nodes.containsKey(value.label())).count();
        long unknown = raw.nodes().size() - recognized;
        long requiringKeys = 0;
        long withKeys = 0;
        long typeConflicts = 0;
        long missingRequired = 0;
        for (GraphExtractionResult.ExtractedNode node : raw.nodes()) {
            SchemaDocument.NodeDefinition definition = nodes.get(node.label());
            if (definition == null) continue;
            Map<String, Object> properties = node.properties() == null ? Map.of() : node.properties();
            if (definition.key() != null && !definition.key().isEmpty()) {
                requiringKeys++;
                if (definition.key().stream().allMatch(key -> present(properties.get(key)))) withKeys++;
            }
            for (SchemaDocument.PropertyDefinition property : safe(definition.properties())) {
                Object value = properties.get(property.name());
                if (Boolean.TRUE.equals(property.required()) && !present(value)) missingRequired++;
                if (present(value) && !compatible(property.type(), value)) typeConflicts++;
            }
        }
        Map<String, Long> reasons = new LinkedHashMap<>();
        for (GraphExtractionResult.ExtractedRelationship relationship : raw.relationships()) {
            if (!relationships.contains(relationshipKey(
                relationship.type(), relationship.fromLabel(), relationship.toLabel()))) {
                reasons.merge("schema_constraint", 1L, Long::sum);
            }
        }
        long dropped = Math.max(0, raw.relationships().size() - sanitized.relationships().size());
        long classified = reasons.values().stream().mapToLong(Long::longValue).sum();
        if (dropped > classified) reasons.put("invalid_endpoint_or_key", dropped - classified);
        return new Metrics(recognized, unknown, Rate.of(recognized, recognized + unknown), raw.relationships().size(),
            dropped, Rate.of(dropped, raw.relationships().size()), requiringKeys, withKeys,
            Rate.of(withKeys, requiringKeys), typeConflicts, missingRequired, lowSupportCandidates,
            guidedWithoutEvidenceCandidates, Map.copyOf(reasons));
    }

    public Metrics combine(List<Metrics> values) {
        long recognized = sum(values, Metrics::recognizedEntities);
        long unknown = sum(values, Metrics::unknownEntities);
        long relationships = sum(values, Metrics::relationships);
        long dropped = sum(values, Metrics::droppedRelationships);
        long requiringKeys = sum(values, Metrics::nodesRequiringKeys);
        long withKeys = sum(values, Metrics::nodesWithKeys);
        Map<String, Long> reasons = new LinkedHashMap<>();
        values.forEach(value -> value.droppedRelationshipReasons().forEach((key, count) -> reasons.merge(key, count, Long::sum)));
        return new Metrics(recognized, unknown, Rate.of(recognized, recognized + unknown), relationships, dropped,
            Rate.of(dropped, relationships), requiringKeys, withKeys, Rate.of(withKeys, requiringKeys),
            sum(values, Metrics::propertyTypeConflicts), sum(values, Metrics::missingRequiredProperties),
            values.stream().mapToLong(Metrics::lowSupportCandidates).max().orElse(0),
            values.stream().mapToLong(Metrics::guidedWithoutEvidenceCandidates).max().orElse(0), Map.copyOf(reasons));
    }

    private long sum(List<Metrics> values, java.util.function.ToLongFunction<Metrics> mapper) {
        return values.stream().mapToLong(mapper).sum();
    }
    private String relationshipKey(String type, String from, String to) { return type + "|" + from + "|" + to; }
    private boolean present(Object value) { return value != null && !(value instanceof String text && text.isBlank()); }
    private boolean compatible(String type, Object value) {
        if (type == null) return true;
        String normalized = type.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STRING" -> value instanceof String;
            case "INTEGER", "LONG" -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case "FLOAT", "DOUBLE", "NUMBER" -> value instanceof Number;
            case "BOOLEAN" -> value instanceof Boolean;
            case "LIST", "ARRAY" -> value instanceof List<?>;
            case "OBJECT", "MAP" -> value instanceof Map<?, ?>;
            default -> true;
        };
    }
    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
}
