package io.github.vfedoriv.graphrag.domain;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.ComparisonOperator;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Direction;
import java.util.List;

public final class AdvancedSearchPlanningContracts {

    public static final int PLAN_VERSION = 1;
    public static final int SUFFICIENCY_VERSION = 1;
    public static final String PLANNER_PROMPT_REVISION = "advanced-search-planner-v1";
    public static final String SUFFICIENCY_PROMPT_REVISION = "advanced-search-sufficiency-v1";

    private AdvancedSearchPlanningContracts() { }

    public record Plan(
        int version,
        List<Subquestion> subquestions,
        List<String> exactTerms,
        MetadataConstraint metadata,
        List<GraphRequest> graphRequests
    ) {
        public Plan {
            subquestions = subquestions == null ? List.of() : List.copyOf(subquestions);
            exactTerms = exactTerms == null ? List.of() : List.copyOf(exactTerms);
            metadata = metadata == null ? new MetadataConstraint(null, null) : metadata;
            graphRequests = graphRequests == null ? List.of() : List.copyOf(graphRequests);
        }
    }

    public record Subquestion(String id, String text) { }

    public record MetadataConstraint(String filename, String contentType) { }

    public record GraphRequest(
        String id,
        String rootLabel,
        List<GraphFilter> filters,
        List<GraphHop> hops,
        List<GraphProjection> projections,
        int limit
    ) {
        public GraphRequest {
            filters = filters == null ? List.of() : List.copyOf(filters);
            hops = hops == null ? List.of() : List.copyOf(hops);
            projections = projections == null ? List.of() : List.copyOf(projections);
        }
    }

    public record GraphFilter(
        int nodeIndex,
        String property,
        ComparisonOperator operator,
        LiteralValue literal
    ) { }

    public enum LiteralType {
        STRING,
        LONG,
        DOUBLE,
        BOOLEAN,
        STRING_LIST,
        LONG_LIST,
        DOUBLE_LIST,
        BOOLEAN_LIST
    }

    public record LiteralValue(LiteralType type, String value, List<String> values) {
        public LiteralValue {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record GraphHop(String relationshipType, String targetLabel, Direction direction) { }

    public record GraphProjection(int nodeIndex, String property, String alias) { }

    public record PlanSummary(
        int version,
        String promptRevision,
        int subquestionCount,
        int exactTermCount,
        int graphRequestCount,
        boolean metadataConstrained,
        boolean fallbackUsed,
        String fallbackCategory
    ) { }

    public enum CoverageStatus {
        COMPLETE,
        PARTIAL,
        MISSING
    }

    public record SufficiencyResult(
        int version,
        List<Coverage> coverage,
        List<Contradiction> contradictions,
        EvidenceGap gap,
        List<Refinement> refinements
    ) {
        public SufficiencyResult {
            coverage = coverage == null ? List.of() : List.copyOf(coverage);
            contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
            refinements = refinements == null ? List.of() : List.copyOf(refinements);
        }
    }

    public record Coverage(String subquestionId, CoverageStatus status, List<String> evidenceIds) {
        public Coverage {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record Contradiction(String category, List<String> evidenceIds) {
        public Contradiction {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    public record EvidenceGap(boolean concrete, String category) { }

    public record Refinement(String id, String query) { }

    public record SufficiencySummary(
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
}
