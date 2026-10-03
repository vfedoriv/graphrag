package io.github.vfedoriv.graphrag.schemas.discovery;

import io.github.vfedoriv.graphrag.schemas.discovery.adapters.model.ModelResponseDiagnostics;

public final class InvalidModelCandidateException extends ModelOutputException {
    public InvalidModelCandidateException(ModelResponseDiagnostics diagnostics) {
        super("Candidate model response violated the candidate contract",
            SourceFailureCode.INVALID_MODEL_CANDIDATE, diagnostics);
    }
}
