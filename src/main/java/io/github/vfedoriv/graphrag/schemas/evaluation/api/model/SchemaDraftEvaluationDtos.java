package io.github.vfedoriv.graphrag.schemas.evaluation.api.model;

import io.github.vfedoriv.graphrag.dto.PageResponse;

import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationOutcomeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public final class SchemaDraftEvaluationDtos {
    private SchemaDraftEvaluationDtos() { }
    public record RevisionRequest(@PositiveOrZero long revision) { }

    public record StartEvaluationRequest(
        @PositiveOrZero long revision,
        @NotNull List<@NotBlank String> documentIds,
        boolean advisoryEnabled
    ) { }

    public record StartEvaluationResponse(String runId, SchemaDraftEvaluationStatus status, String statusLocation) { }

    public enum MetricApplicability { APPLICABLE, NOT_APPLICABLE }
    public enum MetricIdentifier {
        RECOGNIZED_ENTITY_RATE, DROPPED_RELATIONSHIP_RATE, KEY_AVAILABILITY_RATE,
        RECOGNIZED_ENTITIES, UNKNOWN_ENTITIES, RELATIONSHIPS, DROPPED_RELATIONSHIPS,
        NODES_REQUIRING_KEYS, NODES_WITH_KEYS, PROPERTY_TYPE_CONFLICTS,
        MISSING_REQUIRED_PROPERTIES, LOW_SUPPORT_CANDIDATES, GUIDED_WITHOUT_EVIDENCE_CANDIDATES
    }
    public record MetricEvidenceResponse(String coordinate) { }
    public record RateMetricResponse(
        MetricIdentifier metric, long numerator, long denominator, Double value,
        MetricApplicability applicability, List<MetricEvidenceResponse> evidence
    ) { }
    public record CountMetricResponse(
        MetricIdentifier metric, long count, List<MetricEvidenceResponse> evidence
    ) { }
    public record EvaluationReasonResponse(String code, String detail) { }
    public record EvaluationMetricsResponse(
        List<RateMetricResponse> rates, List<CountMetricResponse> counts,
        List<EvaluationReasonResponse> reasons
    ) { }

    public enum AdvisoryExecutionStatus { NOT_REQUESTED, COMPLETED, COMPLETED_WITHOUT_MODEL_JUDGMENT, FAILED }
    public enum QuestionCoverage { SUPPORTED, PARTIALLY_SUPPORTED, UNSUPPORTED, UNASSESSED }
    public record AdvisoryReasonResponse(String code, String detail) { }
    public record QuestionAssessmentResponse(
        String questionFingerprint, QuestionCoverage coverage, List<AdvisoryReasonResponse> reasons,
        List<String> schemaCoordinates
    ) { }
    public record CoordinateAssessmentResponse(
        String schemaCoordinate, String assessment, List<AdvisoryReasonResponse> reasons
    ) { }
    public record EvaluationReproducibilityResponse(
        String profileId, long profileRevision, String promptRevision, String contractRevision
    ) { }
    public record AdvisoryAssessmentResponse(
        AdvisoryExecutionStatus status, List<QuestionAssessmentResponse> intendedQuestions,
        List<CoordinateAssessmentResponse> schemaNoise, List<AdvisoryReasonResponse> reasons,
        List<String> warnings, EvaluationReproducibilityResponse reproducibility
    ) { }

    public record EvaluationOutcomeResponse(
        String id, String documentId, String documentSha256, SchemaDraftEvaluationOutcomeStatus status,
        boolean reused, int chunkCount, EvaluationMetricsResponse metrics, List<String> evidenceCoordinates,
        String failureCategory, boolean retryable, Instant startedAt, Instant completedAt
    ) { }

    @Schema(name = "SchemaDraftEvaluationOutcomePage")
    public static final class EvaluationOutcomePageResponse extends PageResponse<EvaluationOutcomeResponse> {
        public EvaluationOutcomePageResponse(int page, int size, long totalElements, List<EvaluationOutcomeResponse> content) {
            super(page, size, totalElements, content);
        }
    }

    public record EvaluationRunResponse(
        String id, SchemaDraftEvaluationStatus status, long draftRevision, String aggregateRevisionId,
        String projectionContentHash, String aiProfileId, long aiProfileRevision, String promptRevision,
        String contractRevision, String retryOfRunId, int totalDocuments, int succeededDocuments,
        int failedDocuments, int staleDocuments, EvaluationMetricsResponse metrics,
        AdvisoryAssessmentResponse advisoryAssessment,
        String failureCategory, boolean retryable, Instant createdAt, Instant startedAt, Instant completedAt,
        EvaluationOutcomePageResponse outcomes
    ) { }

    public record EvaluationRunSummaryResponse(
        String id, SchemaDraftEvaluationStatus status, long draftRevision, String aggregateRevisionId,
        String projectionContentHash, String aiProfileId, long aiProfileRevision,
        String promptRevision, String contractRevision, String retryOfRunId,
        int totalDocuments, int succeededDocuments, int failedDocuments, int staleDocuments,
        boolean current, boolean retryable, String failureCategory,
        Instant createdAt, Instant startedAt, Instant completedAt, String statusLocation
    ) { }

    @Schema(name = "SchemaDraftEvaluationRunPage")
    public static final class EvaluationRunPageResponse extends PageResponse<EvaluationRunSummaryResponse> {
        public EvaluationRunPageResponse(
            int page, int size, long totalElements, List<EvaluationRunSummaryResponse> content
        ) {
            super(page, size, totalElements, content);
        }
    }

    public enum EvaluationReadiness { READY, NOT_READY }

    public enum EvaluationIneligibilityReason { ACTIVE_DISCOVERY_EVIDENCE, DRAFT_ANALYSIS_REQUIRED }

    public record EvaluationEligibleDocumentResponse(
        String documentId, String filename, String contentType, long sizeBytes, String sha256,
        Instant uploadedAt, boolean eligible, EvaluationIneligibilityReason ineligibilityReason
    ) { }

    @Schema(name = "SchemaDraftEvaluationEligibleDocumentPage")
    public static final class EvaluationEligibleDocumentPageResponse
        extends PageResponse<EvaluationEligibleDocumentResponse> {
        private final long draftRevision;
        private final String currentAggregateId;
        private final EvaluationReadiness readiness;
        private final EvaluationIneligibilityReason blockingReason;

        public EvaluationEligibleDocumentPageResponse(
            long draftRevision, String currentAggregateId, EvaluationReadiness readiness,
            EvaluationIneligibilityReason blockingReason, int page, int size, long totalElements,
            List<EvaluationEligibleDocumentResponse> content
        ) {
            super(page, size, totalElements, content);
            this.draftRevision = draftRevision;
            this.currentAggregateId = currentAggregateId;
            this.readiness = readiness;
            this.blockingReason = blockingReason;
        }

        public long getDraftRevision() {
            return draftRevision;
        }

        public String getCurrentAggregateId() {
            return currentAggregateId;
        }

        public EvaluationReadiness getReadiness() {
            return readiness;
        }

        public EvaluationIneligibilityReason getBlockingReason() {
            return blockingReason;
        }
    }

}
