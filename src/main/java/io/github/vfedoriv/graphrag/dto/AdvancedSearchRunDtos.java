package io.github.vfedoriv.graphrag.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStage;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public final class AdvancedSearchRunDtos {
    private AdvancedSearchRunDtos() { }

    public record CreateRequest(
        @NotBlank @Size(max = 4000) String query,
        @Min(1) @Max(20) Integer maximumEvidence,
        Boolean includeEvidenceText
    ) { }

    public record RunResponse(
        String id,
        String knowledgeBaseId,
        AdvancedSearchRunStatus status,
        AdvancedSearchRunStage stage,
        int completedBranches,
        int totalBranches,
        int evidenceCount,
        boolean cancellationRequested,
        String failureCategory,
        Instant deadlineAt,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Map<String, String> links
    ) { }

    public record ResultResponse(String runId, int payloadVersion, JsonNode result, Instant createdAt) { }
}
