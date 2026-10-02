package io.github.vfedoriv.graphrag.schemas.evaluation.ports;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.EvaluationObservations.Observation;
public interface EvaluationDryExtraction {
    boolean available();
    Observation extract(SchemaDocument schema, String chunk, String aiProfileId);
}
