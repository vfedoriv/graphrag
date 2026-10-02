package io.github.vfedoriv.graphrag.schemas.discovery;

public final class EmptyModelResponseException extends ModelOutputException {
    public EmptyModelResponseException(ModelResponseDiagnostics diagnostics) {
        super("Candidate model response has no usable normal assistant content",
            SourceFailureCode.EMPTY_MODEL_RESPONSE, diagnostics);
    }
}
