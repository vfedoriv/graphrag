package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentEvaluationPreparation;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentDryExtraction;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.EvaluationDocuments;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.EvaluationObservations;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvaluationAdaptersTest {
    @Test void mapsCompleteDocumentInventoryInspectionAndCapturedPreparationWithoutPolicy() throws Exception {
        DocumentEvaluationPreparation documents = mock(DocumentEvaluationPreparation.class);
        EvaluationDocumentsAdapter adapter = new EvaluationDocumentsAdapter(documents);
        Instant uploaded = Instant.parse("2026-07-21T08:15:30Z");
        DocumentEvaluationPreparation.DocumentMetadata metadata = new DocumentEvaluationPreparation.DocumentMetadata("doc", "input.pdf", "application/pdf", 123, "hash", uploaded);
        when(documents.listOwned("kb", 2, 7)).thenReturn(new DocumentEvaluationPreparation.DocumentPage(2, 7, 100, 15, List.of(metadata)));
        when(documents.inspectOwned("kb", "doc")).thenReturn(Optional.of(metadata));
        when(documents.inspectOwned("other", "doc")).thenReturn(Optional.empty());
        DocumentEvaluationPreparation.Source captured = mock(DocumentEvaluationPreparation.Source.class);
        when(documents.captureOwned("kb", "doc")).thenReturn(Optional.of(captured));
        when(captured.metadata()).thenReturn(metadata);
        when(captured.prepare()).thenReturn(new DocumentEvaluationPreparation.PreparedDocument("doc", List.of("chunk-1", "chunk-2")));

        EvaluationDocuments.DocumentPage page = adapter.listOwned("kb", 2, 7);
        EvaluationDocuments.Metadata mapped = adapter.inspectOwned("kb", "doc").orElseThrow();
        assertThat(page).isEqualTo(new EvaluationDocuments.DocumentPage(2, 7, 100, 15, List.of(mapped)));
        assertThat(mapped).isEqualTo(new EvaluationDocuments.Metadata("doc", "input.pdf", "application/pdf", 123, "hash", uploaded));
        assertThat(adapter.inspectOwned("other", "doc")).isEmpty();
        EvaluationDocuments.Source source = adapter.captureOwned("kb", "doc").orElseThrow();
        assertThat(source.metadata()).isEqualTo(mapped);
        assertThat(source.prepare()).isEqualTo(new EvaluationDocuments.Prepared("doc", List.of("chunk-1", "chunk-2")));
        IOException failure = new IOException("private binary path");
        when(captured.prepare()).thenThrow(failure);
        assertThatThrownBy(source::prepare).isSameAs(failure);
        verify(documents, never()).prepareOwned(anyString(), anyString());
    }

    @Test void mapsAllRawAndValidatedObservationFieldsAndRetainsNestedImmutableProperties() {
        DocumentDryExtraction extraction = mock(DocumentDryExtraction.class);
        EvaluationDryExtractionAdapter adapter = new EvaluationDryExtractionAdapter(extraction);
        SchemaDocument schema = new SchemaDocument("schema", 1, null, List.of(), List.of(), List.of(), List.of());
        Map<String, Object> nested = new LinkedHashMap<>();
        List<Object> list = new ArrayList<>(List.of("original"));
        nested.put("values", list);
        DocumentDryExtraction.Node known = new DocumentDryExtraction.Node("Person", Map.of("id", "p", "nested", nested), 0.9);
        DocumentDryExtraction.Node unknown = new DocumentDryExtraction.Node("Unknown", null, null);
        DocumentDryExtraction.Relationship dropped = new DocumentDryExtraction.Relationship("INVALID", "Person", Map.of("id", "p"), "Unknown", Map.of("id", "u"), Map.of("weight", 4), 0.2);
        when(extraction.available()).thenReturn(true);
        when(extraction.extract(schema, "chunk", "profile")).thenReturn(new DocumentDryExtraction.Observation(
            new DocumentDryExtraction.Result(List.of(known, unknown), List.of(dropped)), new DocumentDryExtraction.Result(List.of(known), List.of())));
        nested.put("new", true); list.add("changed");

        assertThat(adapter.available()).isTrue();
        EvaluationObservations.Observation mapped = adapter.extract(schema, "chunk", "profile");
        assertThat(mapped.raw().nodes()).containsExactly(
            new EvaluationObservations.Node("Person", Map.of("id", "p", "nested", Map.of("values", List.of("original"))), 0.9),
            new EvaluationObservations.Node("Unknown", null, null));
        assertThat(mapped.raw().relationships()).containsExactly(new EvaluationObservations.Relationship(
            "INVALID", "Person", Map.of("id", "p"), "Unknown", Map.of("id", "u"), Map.of("weight", 4), 0.2));
        assertThat(mapped.validated().nodes()).containsExactly(mapped.raw().nodes().getFirst());
        assertThat(mapped.validated().relationships()).isEmpty();
        assertThatThrownBy(() -> mapped.raw().nodes().add(mapped.raw().nodes().getFirst())).isInstanceOf(UnsupportedOperationException.class);
        Map<?, ?> frozen = (Map<?, ?>) mapped.raw().nodes().getFirst().properties().get("nested");
        assertThatThrownBy(frozen::clear).isInstanceOf(UnsupportedOperationException.class);
        List<?> frozenList = (List<?>) frozen.get("values");
        assertThatThrownBy(frozenList::clear).isInstanceOf(UnsupportedOperationException.class);
        verify(extraction).extract(schema, "chunk", "profile");
    }

    @Test void evaluationObservationsDefensivelyCopyNestedValuesIndependentlyOfDocumentContracts() {
        List<Object> children = new ArrayList<>(List.of(1));
        Map<String, Object> properties = new LinkedHashMap<>(Map.of("children", children));
        EvaluationObservations.Node node = new EvaluationObservations.Node("Node", properties, null);
        children.add(2); properties.put("changed", true);
        assertThat(node.properties()).containsOnlyKeys("children");
        assertThat(node.properties().get("children")).isEqualTo(List.of(1));
        assertThatThrownBy(node.properties()::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}
