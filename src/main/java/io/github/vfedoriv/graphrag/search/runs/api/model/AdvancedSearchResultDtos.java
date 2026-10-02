package io.github.vfedoriv.graphrag.search.runs.api.model;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts;

import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.AnswerStatus;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.ClaimKind;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.CitationType;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.ConfidenceLevel;
import java.util.List;
import java.util.Map;

/** Public, version-one advanced-search result contract. */
public final class AdvancedSearchResultDtos {
    private AdvancedSearchResultDtos() { }

    public record AdvancedSearchResultV1(
        int payloadVersion,
        AnswerResponse answer,
        List<EvidenceResponse> evidence,
        List<EvidenceResponse> contexts,
        List<GraphFactResponse> graphFacts,
        AnswerDiagnosticsResponse answerDiagnostics,
        PipelineDiagnosticsResponse diagnostics
    ) {
        public AdvancedSearchResultV1 {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            contexts = contexts == null ? List.of() : List.copyOf(contexts);
            graphFacts = graphFacts == null ? List.of() : List.copyOf(graphFacts);
        }
    }

    public record AnswerResponse(
        int version,
        AnswerStatus status,
        String text,
        ConfidenceResponse confidence,
        List<LimitationResponse> limitations,
        List<ClaimResponse> claims
    ) {
        public AnswerResponse {
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
            claims = claims == null ? List.of() : List.copyOf(claims);
        }
    }

    public record ConfidenceResponse(ConfidenceLevel level, double score) { }

    public record LimitationResponse(String code, String description) { }

    public record ClaimResponse(
        String id,
        ClaimKind kind,
        String text,
        List<String> citationIds,
        List<String> graphFactIds,
        List<String> graphEvidenceIds
    ) {
        public ClaimResponse {
            citationIds = citationIds == null ? List.of() : List.copyOf(citationIds);
            graphFactIds = graphFactIds == null ? List.of() : List.copyOf(graphFactIds);
            graphEvidenceIds = graphEvidenceIds == null ? List.of() : List.copyOf(graphEvidenceIds);
        }
    }

    public record SourceRangeResponse(
        Integer sourceStart,
        Integer sourceEnd,
        Integer pageStart,
        Integer pageEnd
    ) { }

    public record EvidenceResponse(
        String citationId,
        CitationType type,
        String chunkId,
        String documentId,
        SourceRangeResponse range,
        String processingRunId,
        String effectiveChunkerRevision,
        String structuralPath,
        String text,
        int rank,
        Double score,
        String sourceFilename,
        String sourceContentType,
        String sourceDisplayLabel
    ) { }

    public record GraphFactResponse(
        String factId,
        List<String> evidenceIds,
        List<String> citationIds
    ) {
        public GraphFactResponse {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
            citationIds = citationIds == null ? List.of() : List.copyOf(citationIds);
        }
    }

    public record AnswerDiagnosticsResponse(
        boolean repairAttempted,
        boolean repairSucceeded,
        boolean abstained,
        int citationCount,
        int claimCount,
        String outcomeCategory
    ) { }

    public record PipelineDiagnosticsResponse(
        PlanDiagnosticsResponse plan,
        SufficiencyDiagnosticsResponse sufficiency,
        FollowUpDiagnosticsResponse followUp,
        List<AttemptDiagnosticsResponse> attempts,
        FusionDiagnosticsResponse fusion,
        GraphExpansionDiagnosticsResponse graphExpansion,
        ParentContextDiagnosticsResponse parentContext,
        RerankDiagnosticsResponse rerank,
        SelectionDiagnosticsResponse selection,
        SourceMetadataDiagnosticsResponse sourceMetadata
    ) {
        public PipelineDiagnosticsResponse {
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
        }
    }

    public record PlanDiagnosticsResponse(
        int version,
        String promptRevision,
        int subquestionCount,
        int exactTermCount,
        int graphRequestCount,
        boolean metadataConstrained,
        boolean fallbackUsed,
        String fallbackCategory
    ) { }

    public record SufficiencyDiagnosticsResponse(
        int version,
        String promptRevision,
        int completeCoverageCount,
        int partialCoverageCount,
        int missingCoverageCount,
        int contradictionCount,
        boolean concreteGap,
        int refinementCount,
        boolean fallbackUsed,
        String fallbackCategory
    ) { }

    public record FollowUpDiagnosticsResponse(
        boolean executed,
        int queryCount,
        String skippedCategory
    ) { }

    public record AttemptDiagnosticsResponse(
        int roundNumber,
        String subqueryId,
        String retriever,
        String status,
        int candidateCount,
        long latencyMs,
        String failureCategory
    ) { }

    public record FusionDiagnosticsResponse(
        Map<String, Integer> acceptedByChannel,
        Map<String, Integer> truncatedByChannel,
        Map<String, Integer> executedSubqueries,
        int deduplicatedCandidateCount,
        int poolTruncatedCount,
        int graphDerivedCandidateCount
    ) { }

    public record GraphExpansionDiagnosticsResponse(
        int seedCount,
        int sourceRowCount,
        int attachedFactCount
    ) { }

    public record ParentContextDiagnosticsResponse(
        int evidenceConsidered,
        int contextCount,
        int tokenEstimate,
        Map<String, Integer> outcomes
    ) { }

    public record RerankDiagnosticsResponse(int poolSize, boolean fallbackUsed, String fallbackCategory) { }

    public record SelectionDiagnosticsResponse(
        int requestedMaximum,
        int effectivePerDocumentCap,
        boolean comparisonPolicy,
        int skippedForDiversity,
        Map<String, Integer> selectedByDocument
    ) { }

    public record SourceMetadataDiagnosticsResponse(List<String> warnings) {
        public SourceMetadataDiagnosticsResponse {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
