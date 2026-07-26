package io.github.vfedoriv.graphrag.discovery;

import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.FailureCategory;
import java.util.List;

public record SourceFailureDecision(
    FailureCategory category,
    SourceFailureCode code,
    boolean retryable,
    Integer providerStatus,
    List<String> exceptionTypes,
    String rootExceptionType,
    String messageFingerprint
) {
    public SourceFailureDecision {
        exceptionTypes = exceptionTypes == null ? List.of() : List.copyOf(exceptionTypes);
    }
}
