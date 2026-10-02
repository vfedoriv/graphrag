package io.github.vfedoriv.graphrag.schemas.discovery;

public abstract class ModelOutputException extends RuntimeException {
    private final SourceFailureCode failureCode;
    private final ModelResponseDiagnostics diagnostics;

    protected ModelOutputException(
        String safeMessage, SourceFailureCode failureCode, ModelResponseDiagnostics diagnostics
    ) {
        super(safeMessage);
        this.failureCode = failureCode;
        this.diagnostics = diagnostics == null ? ModelResponseDiagnostics.none() : diagnostics;
    }

    public SourceFailureCode failureCode() {
        return failureCode;
    }

    public ModelResponseDiagnostics diagnostics() {
        return diagnostics;
    }
}
