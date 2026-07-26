package io.github.vfedoriv.graphrag.discovery;

public class DiscoveryDeadlineExceededException extends RuntimeException {
    private final SourceFailureCode failureCode;

    public DiscoveryDeadlineExceededException(SourceFailureCode failureCode) {
        super(failureCode.name());
        if (failureCode != SourceFailureCode.SOURCE_DEADLINE_EXCEEDED
            && failureCode != SourceFailureCode.REQUEST_DEADLINE_EXCEEDED) {
            throw new IllegalArgumentException("Unsupported discovery deadline code: " + failureCode);
        }
        this.failureCode = failureCode;
    }

    public SourceFailureCode failureCode() {
        return failureCode;
    }
}
