package io.github.vfedoriv.graphrag.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.CandidateKind;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.Evidence;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.EvidenceOrigin;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.ReviewState;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

public final class SchemaDraftDtos {
    private SchemaDraftDtos() { }

    public record CreateDraftRequest(
        @NotBlank String targetName,
        @Min(1) int targetVersion,
        String baseSchemaId,
        @Valid DraftGuidance guidance
    ) { }

    public record UpdateDraftRequest(
        @PositiveOrZero long revision,
        @NotBlank String targetName,
        @Min(1) int targetVersion
    ) { }

    @JsonDeserialize(using = DraftGuidanceDeserializer.class)
    @tools.jackson.databind.annotation.JsonDeserialize(using = DraftGuidanceJackson3Deserializer.class)
    public record DraftGuidance(
        @Size(max = 20_000) String additionalInstructions,
        @Valid SchemaDiscoveryRequest.DiscoveryGuidance guidance
    ) {
        public DraftGuidance {
            guidance = guidance == null ? SchemaDiscoveryRequest.DiscoveryGuidance.empty() : guidance;
        }

        public static DraftGuidance empty() {
            return new DraftGuidance(null, SchemaDiscoveryRequest.DiscoveryGuidance.empty());
        }
    }

    public static final class DraftGuidanceDeserializer extends JsonDeserializer<DraftGuidance> {
        private static final Set<String> ENVELOPE = Set.of("additionalInstructions", "guidance");
        private static final Set<String> GUIDANCE = Set.of(
            "domainDescription", "intendedQuestions", "requiredConcepts", "preferredConcepts",
            "excludedConcepts", "namingRules", "propertyRules", "relationshipRules");
        private static final Set<String> CONCEPT = Set.of("name", "description", "identityKeys");
        private static final Set<String> NAMING = Set.of("nodeLabels", "relationshipTypes", "properties");
        private static final Set<String> PROPERTY = Set.of("owner", "name", "type", "identity", "required");
        private static final Set<String> RELATIONSHIP = Set.of("type", "from", "to", "cardinality");

        @Override
        public DraftGuidance deserialize(JsonParser parser, DeserializationContext context) throws java.io.IOException {
            ObjectMapper mapper = (ObjectMapper) parser.getCodec();
            JsonNode root = mapper.readTree(parser);
            requireObject(root, ENVELOPE, "draft guidance");
            JsonNode structured = root.get("guidance");
            SchemaDiscoveryRequest.DiscoveryGuidance guidance = SchemaDiscoveryRequest.DiscoveryGuidance.empty();
            if (structured != null && !structured.isNull()) {
                requireObject(structured, GUIDANCE, "structured guidance");
                validateObjectArray(structured.get("requiredConcepts"), CONCEPT, "required concept");
                validateObjectArray(structured.get("preferredConcepts"), CONCEPT, "preferred concept");
                validateObject(structured.get("namingRules"), NAMING, "naming rules");
                validateObjectArray(structured.get("propertyRules"), PROPERTY, "property rule");
                validateObjectArray(structured.get("relationshipRules"), RELATIONSHIP, "relationship rule");
                guidance = mapper.treeToValue(structured, SchemaDiscoveryRequest.DiscoveryGuidance.class);
            }
            JsonNode instructions = root.get("additionalInstructions");
            if (instructions != null && !instructions.isNull() && !instructions.isTextual()) {
                throw context.weirdStringException(instructions.toString(), String.class,
                    "additionalInstructions must be a string");
            }
            return new DraftGuidance(instructions == null || instructions.isNull() ? null : instructions.asText(), guidance);
        }

        private void validateObject(JsonNode value, Set<String> fields, String name) throws java.io.IOException {
            if (value != null && !value.isNull()) {
                requireObject(value, fields, name);
            }
        }

        private void validateObjectArray(JsonNode value, Set<String> fields, String name) throws java.io.IOException {
            if (value == null || value.isNull()) {
                return;
            }
            if (!value.isArray()) {
                throw new com.fasterxml.jackson.databind.JsonMappingException(null, name + " collection must be an array");
            }
            for (JsonNode item : value) {
                requireObject(item, fields, name);
            }
        }

        private void requireObject(JsonNode value, Set<String> fields, String name) throws java.io.IOException {
            if (value == null || !value.isObject()) {
                throw new com.fasterxml.jackson.databind.JsonMappingException(null, name + " must be an object");
            }
            java.util.Iterator<String> names = value.fieldNames();
            while (names.hasNext()) {
                String field = names.next();
                if (!fields.contains(field)) {
                    throw new com.fasterxml.jackson.databind.JsonMappingException(null,
                        "Unknown " + name + " field: " + field);
                }
            }
        }
    }

    public static final class DraftGuidanceJackson3Deserializer
        extends tools.jackson.databind.ValueDeserializer<DraftGuidance> {
        private static final Set<String> ENVELOPE = Set.of("additionalInstructions", "guidance");
        private static final Set<String> GUIDANCE = Set.of(
            "domainDescription", "intendedQuestions", "requiredConcepts", "preferredConcepts",
            "excludedConcepts", "namingRules", "propertyRules", "relationshipRules");
        private static final Set<String> CONCEPT = Set.of("name", "description", "identityKeys");
        private static final Set<String> NAMING = Set.of("nodeLabels", "relationshipTypes", "properties");
        private static final Set<String> PROPERTY = Set.of("owner", "name", "type", "identity", "required");
        private static final Set<String> RELATIONSHIP = Set.of("type", "from", "to", "cardinality");

        @Override
        public DraftGuidance deserialize(
            tools.jackson.core.JsonParser parser, tools.jackson.databind.DeserializationContext context
        ) throws tools.jackson.core.JacksonException {
            tools.jackson.databind.JsonNode root = context.readTree(parser);
            requireObject(context, root, ENVELOPE, "draft guidance");
            tools.jackson.databind.JsonNode structured = root.get("guidance");
            SchemaDiscoveryRequest.DiscoveryGuidance guidance = SchemaDiscoveryRequest.DiscoveryGuidance.empty();
            if (structured != null && !structured.isNull()) {
                requireObject(context, structured, GUIDANCE, "structured guidance");
                validateObjectArray(context, structured.get("requiredConcepts"), CONCEPT, "required concept");
                validateObjectArray(context, structured.get("preferredConcepts"), CONCEPT, "preferred concept");
                validateObject(context, structured.get("namingRules"), NAMING, "naming rules");
                validateObjectArray(context, structured.get("propertyRules"), PROPERTY, "property rule");
                validateObjectArray(context, structured.get("relationshipRules"), RELATIONSHIP, "relationship rule");
                guidance = context.readTreeAsValue(structured, SchemaDiscoveryRequest.DiscoveryGuidance.class);
            }
            tools.jackson.databind.JsonNode instructions = root.get("additionalInstructions");
            if (instructions != null && !instructions.isNull() && !instructions.isTextual()) {
                return context.reportInputMismatch(DraftGuidance.class, "additionalInstructions must be a string");
            }
            return new DraftGuidance(instructions == null || instructions.isNull() ? null : instructions.asString(), guidance);
        }

        private void validateObject(
            tools.jackson.databind.DeserializationContext context, tools.jackson.databind.JsonNode value,
            Set<String> fields, String name
        ) throws tools.jackson.core.JacksonException {
            if (value != null && !value.isNull()) {
                requireObject(context, value, fields, name);
            }
        }

        private void validateObjectArray(
            tools.jackson.databind.DeserializationContext context, tools.jackson.databind.JsonNode value,
            Set<String> fields, String name
        ) throws tools.jackson.core.JacksonException {
            if (value == null || value.isNull()) {
                return;
            }
            if (!value.isArray()) {
                context.reportInputMismatch(DraftGuidance.class, name + " collection must be an array");
            }
            for (tools.jackson.databind.JsonNode item : value) {
                requireObject(context, item, fields, name);
            }
        }

        private void requireObject(
            tools.jackson.databind.DeserializationContext context, tools.jackson.databind.JsonNode value,
            Set<String> fields, String name
        ) throws tools.jackson.core.JacksonException {
            if (value == null || !value.isObject()) {
                context.reportInputMismatch(DraftGuidance.class, name + " must be an object");
            }
            for (Map.Entry<String, tools.jackson.databind.JsonNode> entry : value.properties()) {
                if (!fields.contains(entry.getKey())) {
                    context.reportInputMismatch(DraftGuidance.class,
                        "Unknown " + name + " field: " + entry.getKey());
                }
            }
        }
    }

    public record UpdateGuidanceRequest(@PositiveOrZero long revision, @NotNull @Valid DraftGuidance guidance) { }
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
        DraftGuidance guidance,
        long guidanceRevision,
        String guidanceFingerprint,
        String currentAggregateId,
        String publicationSchemaId,
        String publicationContentHash,
        String currentPublishedSchemaContentHash,
        boolean publicationContentDrifted,
        String activeAiProfileId,
        long activeAiProfileRevision,
        AnalysisWorkflowReference currentAnalysis,
        EvaluationWorkflowReference latestEvaluation,
        ReprocessingWorkflowReference latestReprocessing,
        Instant createdAt,
        Instant updatedAt
    ) { }

    public record AnalysisWorkflowReference(
        String id, SchemaDraftAnalysisStatus status, boolean current, String statusLocation
    ) { }

    public record EvaluationWorkflowReference(
        String id, SchemaDraftEvaluationStatus status, boolean current, boolean latest, String statusLocation
    ) { }

    public record ReprocessingWorkflowReference(
        String id, io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanStatus status,
        boolean targetCurrent, boolean latest, String statusLocation
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

    @Schema(name = "SchemaDraftAnalysisSourceOutcomePage")
    public static final class SourceOutcomePageResponse extends PageResponse<SourceOutcomeResponse> {
        public SourceOutcomePageResponse(int page, int size, long totalElements, List<SourceOutcomeResponse> content) {
            super(page, size, totalElements, content);
        }
    }

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
        String retryOfRunId,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        SourceOutcomePageResponse sourceOutcomes
    ) { }

    public record AnalysisRunSummaryResponse(
        String id, SchemaDraftAnalysisStatus status, long draftRevision, long guidanceRevision,
        int totalSources, int succeededSources, int failedSources, boolean current,
        String aggregateRevisionId, String failureCategory, boolean retryable, String retryOfRunId,
        Instant createdAt, Instant startedAt, Instant completedAt, String statusLocation
    ) { }

    @Schema(name = "SchemaDraftAnalysisRunPage")
    public static final class AnalysisRunPageResponse extends PageResponse<AnalysisRunSummaryResponse> {
        public AnalysisRunPageResponse(
            int page, int size, long totalElements, List<AnalysisRunSummaryResponse> content
        ) {
            super(page, size, totalElements, content);
        }
    }

    public record CandidateResponse(
        CandidateKind kind,
        String identity,
        String label,
        String property,
        String propertyType,
        List<String> keys,
        String relationshipType,
        String fromLabel,
        String toLabel,
        String originalLabel,
        String originalProperty,
        String originalRelationshipType,
        Double confidence,
        Set<EvidenceOrigin> origins,
        List<Evidence> evidence,
        int supportCount,
        ReviewState recommendationState,
        SchemaDraftReviewState effectiveReviewState,
        String latestDecisionId
    ) { }

    @Schema(name = "SchemaDraftCandidatePage")
    public static final class CandidatePageResponse extends PageResponse<CandidateResponse> {
        public CandidatePageResponse(int page, int size, long totalElements, List<CandidateResponse> content) {
            super(page, size, totalElements, content);
        }
    }

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
