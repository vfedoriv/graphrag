package io.github.vfedoriv.graphrag.discovery;

public final class InvalidModelCandidateException extends ModelOutputException {
    public InvalidModelCandidateException(ModelResponseDiagnostics diagnostics) {
        super("Candidate model response violated the candidate contract",
            SourceFailureCode.INVALID_MODEL_CANDIDATE, diagnostics);
    }
}
