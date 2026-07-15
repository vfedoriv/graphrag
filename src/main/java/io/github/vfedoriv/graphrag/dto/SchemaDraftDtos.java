package io.github.vfedoriv.graphrag.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftCompatibility;
import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftDecisionType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftReviewState;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;

public final class SchemaDraftDtos {
    private SchemaDraftDtos() { }

    public record CreateDraftRequest(
        @NotBlank String targetName,
        @Min(1) int targetVersion,
        String baseSchemaId,
        Object guidance
    ) { }

    public record UpdateDraftRequest(
        @PositiveOrZero long revision,
        @NotBlank String targetName,
        @Min(1) int targetVersion
    ) { }

    public record UpdateGuidanceRequest(@PositiveOrZero long revision, Object guidance) { }
    public record AddDocumentSourceRequest(@PositiveOrZero long revision, @NotBlank String documentId) { }
    public record AddTextSourceRequest(@PositiveOrZero long revision, @NotBlank String name, @NotBlank String text) { }
    public record RevisionRequest(@PositiveOrZero long revision) { }

    public record DraftResponse(
        String id,
        String knowledgeBaseId,
        String targetName,
        int targetVersion,
        String baseSchemaId,
        SchemaDraftStatus status,
        long revision,
        long guidanceRevision,
        String guidanceFingerprint,
        String currentAggregateId,
        String publicationSchemaId,
        String publicationContentHash,
        String currentPublishedSchemaContentHash,
        boolean publicationContentDrifted,
        String activeAiProfileId,
        long activeAiProfileRevision,
        Instant createdAt,
        Instant updatedAt
    ) { }

    public record SourceResponse(
        String id,
        SchemaDraftSourceType type,
        SchemaDraftSourceStatus status,
        long revision,
        String documentId,
        String name,
        String contentType,
        long sizeBytes,
        String sha256,
        boolean analyzed,
        Instant createdAt,
        Instant updatedAt
    ) { }

    public record StartAnalysisResponse(String runId, SchemaDraftAnalysisStatus status, String statusLocation) { }

    public record SourceOutcomeResponse(
        String id,
        String sourceId,
        long sourceRevision,
        SchemaDraftSourceResultStatus status,
        boolean reused,
        String failureCategory,
        boolean retryable,
        int chunkCount,
        Instant completedAt
    ) { }

    public record AnalysisRunResponse(
        String id,
        SchemaDraftAnalysisStatus status,
        long draftRevision,
        long guidanceRevision,
        String aiProfileId,
        long aiProfileRevision,
        String promptRevision,
        String candidateRevision,
        int totalSources,
        int succeededSources,
        int failedSources,
        boolean currentResult,
        String aggregateRevisionId,
        String failureCategory,
        boolean retryable,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        List<SourceOutcomeResponse> sourceOutcomes,
        long sourceOutcomeCount
    ) { }

    public record DecisionRequest(
        @PositiveOrZero long revision,
        @NotNull SchemaDraftDecisionType type,
        @NotBlank String candidateIdentity,
        Object resultingValue,
        String rationale
    ) { }

    public record DecisionResponse(
        String id,
        long sequence,
        long draftRevision,
        SchemaDraftDecisionType type,
        SchemaDraftReviewState reviewState,
        String candidateIdentity,
        Object priorValue,
        Object resultingValue,
        String rationale,
        Instant createdAt
    ) { }

    public record ResolveConflictRequest(
        @PositiveOrZero long revision,
        String selectedAlternative,
        Object customResolution,
        String rationale
    ) { }

    public record ConflictResponse(
        String id,
        SchemaDraftConflictType type,
        String coordinate,
        Object alternatives,
        Object evidence,
        boolean resolved,
        String selectedAlternative,
        Object customResolution,
        Instant createdAt,
        Instant resolvedAt
    ) { }

    public record ProjectionResponse(String aggregateRevisionId, long draftRevision, Object schema, boolean publicationReady) { }
    public record DiffItem(String coordinate, SchemaDraftCompatibility compatibility, String operation, Object before, Object after) { }
    public record DiffResponse(String aggregateRevisionId, List<DiffItem> changes) { }

    public record StartEvaluationRequest(
        @PositiveOrZero long revision,
        @NotNull List<@NotBlank String> documentIds,
        boolean advisoryEnabled
    ) { }

    public record StartEvaluationResponse(String runId, SchemaDraftEvaluationStatus status, String statusLocation) { }

    public record EvaluationOutcomeResponse(
        String id, String documentId, String documentSha256, SchemaDraftEvaluationOutcomeStatus status,
        boolean reused, int chunkCount, Object metrics, List<String> evidenceCoordinates,
        String failureCategory, boolean retryable, Instant startedAt, Instant completedAt
    ) { }

    public record EvaluationRunResponse(
        String id, SchemaDraftEvaluationStatus status, long draftRevision, String aggregateRevisionId,
        String projectionContentHash, String aiProfileId, long aiProfileRevision, String promptRevision,
        String contractRevision, String retryOfRunId, int totalDocuments, int succeededDocuments,
        int failedDocuments, int staleDocuments, Object metrics, Object advisoryAssessment,
        String failureCategory, boolean retryable, Instant createdAt, Instant startedAt, Instant completedAt,
        List<EvaluationOutcomeResponse> outcomes, long outcomeCount
    ) { }

    public record ReadinessBlockingReason(String id, String category, String detail) { }

    public record PublicationReadinessResponse(
        boolean ready, long draftRevision, String aggregateRevisionId, String projectionContentHash,
        String targetName, int targetVersion, List<ReadinessBlockingReason> blockingReasons
    ) { }

    public record PublishDraftRequest(@PositiveOrZero long revision, @NotBlank String projectionContentHash) { }

    public record PublicationResponse(
        String publicationId, String draftId, String schemaId, long draftRevision,
        String publicationContentHash, String currentSchemaContentHash, boolean contentDrifted,
        boolean active, Instant publishedAt
    ) { }
}
