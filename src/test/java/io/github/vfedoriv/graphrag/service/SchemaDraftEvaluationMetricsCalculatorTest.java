package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationContracts.Metrics;
import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaDraftEvaluationMetricsCalculatorTest {
    private final SchemaDraftEvaluationMetricsCalculator calculator = new SchemaDraftEvaluationMetricsCalculator();

    @Test
    void calculatesContractualRatesAndValidationCounts() {
        SchemaDocument schema = schema();
        GraphExtractionResult raw = new GraphExtractionResult(
            List.of(
                new GraphExtractionResult.ExtractedNode("Person", Map.of("personId", "p-1", "age", "wrong"), 1.0),
                new GraphExtractionResult.ExtractedNode("Person", Map.of(), 1.0),
                new GraphExtractionResult.ExtractedNode("Unknown", Map.of(), 1.0)
            ),
            List.of(new GraphExtractionResult.ExtractedRelationship(
                "UNKNOWN", "Person", Map.of("personId", "p-1"), "Person", Map.of("personId", "p-2"), Map.of(), 1.0))
        );
        GraphExtractionResult sanitized = new GraphExtractionResult(List.of(raw.nodes().getFirst()), List.of());

        Metrics metrics = calculator.calculate(raw, sanitized, schema, 2, 1);

        assertThat(metrics.recognizedEntities()).isEqualTo(2);
        assertThat(metrics.unknownEntities()).isEqualTo(1);
        assertThat(metrics.recognizedEntityRate().value()).isEqualTo(2.0 / 3.0);
        assertThat(metrics.droppedRelationshipRate().value()).isEqualTo(1.0);
        assertThat(metrics.keyAvailabilityRate().value()).isEqualTo(0.5);
        assertThat(metrics.propertyTypeConflicts()).isEqualTo(1);
        assertThat(metrics.missingRequiredProperties()).isEqualTo(1);
        assertThat(metrics.lowSupportCandidates()).isEqualTo(2);
        assertThat(metrics.guidedWithoutEvidenceCandidates()).isEqualTo(1);
    }

    @Test
    void reportsEmptyDenominatorsAsNotApplicable() {
        Metrics metrics = calculator.calculate(
            new GraphExtractionResult(List.of(), List.of()),
            new GraphExtractionResult(List.of(), List.of()), schema(), 0, 0);

        assertThat(metrics.recognizedEntityRate().applicable()).isFalse();
        assertThat(metrics.recognizedEntityRate().value()).isNull();
        assertThat(metrics.droppedRelationshipRate().applicable()).isFalse();
        assertThat(metrics.keyAvailabilityRate().applicable()).isFalse();
    }

    private SchemaDocument schema() {
        return new SchemaDocument("people", 1, null,
            List.of(new SchemaDocument.NodeDefinition("Person", null, List.of("personId"), List.of(
                new SchemaDocument.PropertyDefinition("personId", "STRING", true),
                new SchemaDocument.PropertyDefinition("age", "INTEGER", false)))),
            List.of(), List.of(), List.of());
    }
}
