package io.github.vfedoriv.graphrag.schemas.drafts.api.model;

import io.github.vfedoriv.graphrag.dto.PageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.CandidateKind;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.Evidence;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.EvidenceOrigin;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.ReviewState;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureCode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftCompatibility;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftConflictType;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftDecisionType;
import io.github.vfedoriv.graphrag.domain.DiffBaselineType;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftReviewState;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceResultStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceStatus;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceType;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStatus;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationStatus;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationOutcomeStatus;
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
        String id, io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanStatus status,
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
        SourceFailureCode failureCode,
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
        Integer effectiveSourceConcurrency,
        Long effectiveSourceTimeoutMillis,
        Long effectiveRequestTimeoutMillis,
        int totalSources,
        int succeededSources,
        int failedSources,
        boolean currentResult,
        String aggregateRevisionId,
        String failureCategory,
        @Schema(description = "Persisted classification indicating whether the run contains a retryable failure.")
        boolean retryable,
        @Schema(description = "Current deterministic resource-state eligibility for requesting a child retry run. "
            + "The retry command revalidates current state and may still reject a race or capacity failure.")
        boolean canRetry,
        String retryOfRunId,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        SourceOutcomePageResponse sourceOutcomes
    ) { }

    public record AnalysisRunSummaryResponse(
        String id, SchemaDraftAnalysisStatus status, long draftRevision, long guidanceRevision,
        Integer effectiveSourceConcurrency, Long effectiveSourceTimeoutMillis, Long effectiveRequestTimeoutMillis,
        int totalSources, int succeededSources, int failedSources, boolean current,
        String aggregateRevisionId, String failureCategory,
        @Schema(description = "Persisted classification indicating whether the run contains a retryable failure. "
            + "History consumers that previously used this field for retry actions must migrate to canRetry.")
        boolean retryable,
        @Schema(description = "Current deterministic resource-state eligibility for requesting a child retry run. "
            + "The retry command revalidates current state and may still reject a race or capacity failure.")
        boolean canRetry,
        String retryOfRunId,
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

    @Schema(description = "Conflict list scope. CURRENT is the active review surface; ALL is immutable draft history.",
        example = "CURRENT")
    public enum ConflictListScope { CURRENT, ALL }

    public record ConflictResponse(
        String id,
        SchemaDraftConflictType type,
        String coordinate,
        Object alternatives,
        Object evidence,
        boolean resolved,
        String selectedAlternative,
        Object customResolution,
        @Schema(description = "Identifier of the immutable aggregate revision that owns this conflict.",
            example = "4f3e8c25-ecaa-41e3-8cf1-58822a93085d")
        String aggregateRevisionId,
        @Schema(description = "True when aggregateRevisionId is the draft's currently promoted aggregate.",
            example = "true")
        boolean current,
        Instant createdAt,
        Instant resolvedAt
    ) { }

    public record ProjectionResponse(String aggregateRevisionId, long draftRevision, Object schema, boolean publicationReady) { }
    public record DiffItem(String coordinate, SchemaDraftCompatibility compatibility, String operation, Object before, Object after) { }
    @Schema(description = "Immutable comparison baseline used to produce the before side of a schema-draft diff.")
    public record DiffBaseline(
        @Schema(description = "Kind of immutable comparison baseline.", example = "PREVIOUS_AGGREGATE")
        DiffBaselineType type,
        @Schema(description = "Base schema ID for BASE_SCHEMA, predecessor aggregate ID for PREVIOUS_AGGREGATE, "
            + "and null for EMPTY.", nullable = true,
            example = "4f3e8c25-ecaa-41e3-8cf1-58822a93085d")
        String id,
        @Schema(description = "Lowercase SHA-256 hash of the exact canonical JSON comparison content.",
            example = "2e1cfa82b035c26cbd95ef85d56f9f635af58a71d92e4a43db6f3f3bbbeadca2")
        String contentHash
    ) { }

    @Schema(description = "Revision-bound compatibility diff. Deploy strict-client support for draftRevision and "
        + "baseline before rolling out this expanded response.")
    public record DiffResponse(
        @Schema(description = "Identifier of the current aggregate used for the reviewed projection.")
        String aggregateRevisionId,
        @Schema(description = "Draft decision revision used to compute the reviewed projection.", example = "3")
        long draftRevision,
        DiffBaseline baseline,
        List<DiffItem> changes
    ) { }

}
