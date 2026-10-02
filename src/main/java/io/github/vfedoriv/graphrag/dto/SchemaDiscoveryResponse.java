package io.github.vfedoriv.graphrag.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.Candidate;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.Conflict;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.FailureCategory;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.ResponseStatus;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.SourceStatus;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.SourceType;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Stateless, review-only multi-source schema discovery result.")
public record SchemaDiscoveryResponse(
    ResponseStatus status,
    List<Candidate> candidates,
    List<Conflict> conflicts,
    List<Warning> warnings,
    List<SourceOutcome> sourceOutcomes,
    JsonNode schema,
    Reproducibility reproducibility
) {
    public record Warning(String code, String coordinate, String message, int supportCount) {
    }

    public record SourceOutcome(
        String sourceId,
        SourceType sourceType,
        String fingerprint,
        SourceStatus status,
        FailureCategory failureCategory,
        SourceFailureCode failureCode,
        boolean retryable,
        int chunkCount
    ) {
    }

    public record Reproducibility(
        String aiProfileId,
        long aiProfileRevision,
        String promptContractRevision,
        String candidateContractRevision,
        String limitsRevision
    ) {
    }
}
