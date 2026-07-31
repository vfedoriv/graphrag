package io.github.vfedoriv.graphrag.dto;

import io.github.vfedoriv.graphrag.domain.ChunkReprocessingSelection;
import io.github.vfedoriv.graphrag.domain.ReprocessingPlanReason;
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
        String draftId, String schemaId, boolean allDocuments,
        List<@NotBlank String> documentIds, Map<String, Object> processingOptions,
        ReprocessingPlanReason reason, ChunkReprocessingSelection selection,
        String expectedChunkerRevision
    ) {
        public CreatePlanRequest(
            String draftId,
            String schemaId,
            boolean allDocuments,
            List<String> documentIds,
            Map<String, Object> processingOptions
        ) {
            this(
                draftId,
                schemaId,
                allDocuments,
                documentIds,
                processingOptions,
                ReprocessingPlanReason.SCHEMA_ACTIVATION,
                null,
                null
            );
        }
    }
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
        String id, ReprocessingPlanReason reason, ChunkReprocessingSelection selection,
        String expectedChunkerRevision, SchemaReprocessingPlanStatus status,
        String draftId, String knowledgeBaseId,
        String schemaId, String schemaContentHash, String aiProfileId, long aiProfileRevision,
        String retryOfPlanId, int totalDocuments, int queuedDocuments, int runningDocuments,
        int succeededDocuments, int failedDocuments, int staleDocuments, int blockedDocuments,
        Instant createdAt, Instant startedAt, Instant completedAt, PlanItemPageResponse items
    ) { }

    public record PlanSummaryResponse(
        String id, ReprocessingPlanReason reason, ChunkReprocessingSelection selection,
        String expectedChunkerRevision, SchemaReprocessingPlanStatus status, String draftId, String schemaId,
        String schemaContentHash, String retryOfPlanId, int totalDocuments, int queuedDocuments,
        int runningDocuments, int succeededDocuments, int failedDocuments, int staleDocuments,
        int blockedDocuments, boolean latest, boolean targetCurrent, boolean retryable,
        Instant createdAt, Instant startedAt, Instant completedAt, String statusLocation
    ) { }

    @Schema(name = "SchemaReprocessingPlanPage")
    public static final class PlanPageResponse extends PageResponse<PlanSummaryResponse> {
        public PlanPageResponse(int page, int size, long totalElements, List<PlanSummaryResponse> content) {
            super(page, size, totalElements, content);
        }
    }
}
