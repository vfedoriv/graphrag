package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentDryExtraction;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.EvaluationObservations.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.EvaluationDryExtraction;
import org.springframework.stereotype.Component;
@Component
public class EvaluationDryExtractionAdapter implements EvaluationDryExtraction {
    private final DocumentDryExtraction extraction;
    public EvaluationDryExtractionAdapter(DocumentDryExtraction extraction) { this.extraction = extraction; }
    @Override public boolean available() { return extraction.available(); }
    @Override public Observation extract(SchemaDocument schema, String chunk, String aiProfileId) {
        DocumentDryExtraction.Observation value = extraction.extract(schema, chunk, aiProfileId);
        return new Observation(map(value.raw()), map(value.validated()));
    }
    private Result map(DocumentDryExtraction.Result value) {
        return new Result(value.nodes().stream().map(node -> new Node(node.label(), node.properties(), node.confidence())).toList(),
            value.relationships().stream().map(relationship -> new Relationship(relationship.type(), relationship.fromLabel(),
                relationship.fromKey(), relationship.toLabel(), relationship.toKey(), relationship.properties(), relationship.confidence())).toList());
    }
}
