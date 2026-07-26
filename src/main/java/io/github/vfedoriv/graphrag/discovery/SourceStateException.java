package io.github.vfedoriv.graphrag.discovery;

public final class SourceStateException extends RuntimeException {
    private final SourceFailureCode failureCode;

    public SourceStateException(SourceFailureCode failureCode) {
        super(failureCode == SourceFailureCode.SOURCE_STALE
            ? "Schema draft source snapshot is stale"
            : "Schema draft source content is unavailable");
        if (failureCode != SourceFailureCode.SOURCE_STALE
            && failureCode != SourceFailureCode.SOURCE_UNAVAILABLE) {
            throw new IllegalArgumentException("Source-state failure code is required");
        }
        this.failureCode = failureCode;
    }

    public SourceFailureCode failureCode() {
        return failureCode;
    }
}
