package io.github.vfedoriv.graphrag.schemas.evaluation.contracts;
import java.util.Optional;
/** Revision-specific immutable evidence used by publication readiness. */
public interface PublicationEvaluationQualifications {
    Requirement requirement();
    Optional<Run> qualifying(String draftId, long revision, String projectionHash);
    record Requirement(boolean requiredForPublication, int minimumSuccessfulDocuments) { }
    record Run(String id, long draftRevision, String projectionContentHash, int succeededDocuments) { }
}
