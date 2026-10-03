package io.github.vfedoriv.graphrag.schemas.discovery;

import io.github.vfedoriv.graphrag.schemas.discovery.adapters.model.ModelResponseDiagnostics;

public final class MalformedModelResponseException extends ModelOutputException {
    public MalformedModelResponseException(ModelResponseDiagnostics diagnostics) {
        super("Candidate model response could not be converted",
            SourceFailureCode.MALFORMED_MODEL_RESPONSE, diagnostics);
    }
}
