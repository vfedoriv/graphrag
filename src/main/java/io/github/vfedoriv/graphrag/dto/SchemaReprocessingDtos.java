package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemStatus;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

public final class SchemaReprocessingDtos {
    private SchemaReprocessingDtos() { }

    public record CreatePlanRequest(
        @NotBlank String draftId, @NotBlank String schemaId, boolean allDocuments,
        List<@NotBlank String> documentIds, Map<String, Object> processingOptions
    ) { }
    public record RetryPlanRequest(boolean resnapshotUnresolvedDocuments) { }
    public record StartPlanResponse(String planId, SchemaReprocessingPlanStatus status, String statusLocation) { }
    public record PlanItemResponse(
        String id, String documentId, String documentSha256, SchemaReprocessingItemStatus status,
        String failureCategory, boolean retryable, String priorItemId, Instant startedAt, Instant completedAt
    ) { }
    @Schema(name = "SchemaReprocessingPlanItemPage")
    public static final class PlanItemPageResponse extends PageResponse<PlanItemResponse> {
        public PlanItemPageResponse(int page, int size, long totalElements, List<PlanItemResponse> content) {
            super(page, size, totalElements, content);
        }
    }
    public record PlanResponse(
        String id, SchemaReprocessingPlanStatus status, String draftId, String knowledgeBaseId,
        String schemaId, String schemaContentHash, String aiProfileId, long aiProfileRevision,
        String retryOfPlanId, int totalDocuments, int queuedDocuments, int runningDocuments,
        int succeededDocuments, int failedDocuments, int staleDocuments, int blockedDocuments,
        Instant createdAt, Instant startedAt, Instant completedAt, PlanItemPageResponse items
    ) { }
}
