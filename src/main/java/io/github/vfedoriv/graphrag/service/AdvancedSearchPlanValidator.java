package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.schemas.registry.application.ActiveSchemaContext;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.BooleanLiteral;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.ComparisonFilter;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.DoubleLiteral;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.GraphPlan;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.ListLiteral;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Literal;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.LongLiteral;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.PropertyProjection;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.PropertyReference;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.StringLiteral;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.TypedHop;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.GraphFilter;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.GraphProjection;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.GraphRequest;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.LiteralType;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.LiteralValue;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.MetadataConstraint;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.Plan;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.PlanSummary;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.Subquestion;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.MetadataConstraints;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import io.github.vfedoriv.graphrag.service.GraphPlanValidationService.ValidationResult;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService.AdvancedSearchSettings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AdvancedSearchPlanValidator {

    private final GraphPlanValidationService graphPlanValidationService;

    public AdvancedSearchPlanValidator(GraphPlanValidationService graphPlanValidationService) {
        this.graphPlanValidationService = graphPlanValidationService;
    }

    public ValidatedPlan validate(
        Plan plan,
        ActiveSchemaContext schemaContext,
        AdvancedSearchSettings settings
    ) {
        if (plan == null || plan.version() != io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.PLAN_VERSION) {
            throw new IllegalArgumentException("plan.version.invalid");
        }
        if (plan.subquestions().isEmpty() || plan.subquestions().size() > settings.planningMaxSubqueries()) {
            throw new IllegalArgumentException("plan.subquestions.limit");
        }
        List<Subquery> subqueries = validateSubquestions(plan.subquestions(), settings);
        List<String> exactTerms = validateExactTerms(plan.exactTerms(), settings);
        MetadataConstraints metadata = validateMetadata(plan.metadata(), settings);
        List<GraphPlan> graphPlans = validateGraphRequests(plan.graphRequests(), schemaContext, settings);
        PlanSummary summary = summary(plan, metadata, false, null);
        return new ValidatedPlan(subqueries, exactTerms, metadata, graphPlans, summary);
    }

    public ValidatedPlan fallback(String query, AdvancedSearchSettings settings, String category) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("query.empty");
        }
        String bounded = bounded(normalized, settings.planningMaxStringCharacters());
        MetadataConstraints metadata = new MetadataConstraints(null, null);
        PlanSummary summary = new PlanSummary(
            io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.PLAN_VERSION,
            io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.PLANNER_PROMPT_REVISION,
            1,
            0,
            0,
            false,
            true,
            sanitize(category)
        );
        return new ValidatedPlan(List.of(new Subquery("fallback-1", bounded)), List.of(), metadata, List.of(), summary);
    }

    private List<Subquery> validateSubquestions(List<Subquestion> values, AdvancedSearchSettings settings) {
        List<Subquery> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Subquestion value : values) {
            if (value == null) {
                throw new IllegalArgumentException("plan.subquestion.null");
            }
            String id = normalize(value.id());
            String text = normalize(value.text());
            if (id.isEmpty() || id.length() > 80 || !ids.add(id)) {
                throw new IllegalArgumentException("plan.subquestion.id");
            }
            if (text.isEmpty() || text.length() > settings.planningMaxStringCharacters()) {
                throw new IllegalArgumentException("plan.subquestion.text");
            }
            result.add(new Subquery(id, text));
        }
        return List.copyOf(result);
    }

    private List<String> validateExactTerms(List<String> values, AdvancedSearchSettings settings) {
        if (values.size() > settings.planningMaxExactTerms()) {
            throw new IllegalArgumentException("plan.exact-terms.limit");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized.isEmpty() || normalized.length() > settings.planningMaxStringCharacters()) {
                throw new IllegalArgumentException("plan.exact-term.invalid");
            }
            if (seen.add(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private MetadataConstraints validateMetadata(MetadataConstraint value, AdvancedSearchSettings settings) {
        String filename = optional(value.filename(), settings.planningMaxStringCharacters());
        String contentType = optional(value.contentType(), 255);
        return new MetadataConstraints(filename, contentType);
    }

    private List<GraphPlan> validateGraphRequests(
        List<GraphRequest> requests,
        ActiveSchemaContext schemaContext,
        AdvancedSearchSettings settings
    ) {
        if (requests.size() > settings.planningMaxGraphRequests()) {
            throw new IllegalArgumentException("plan.graph-requests.limit");
        }
        if (!requests.isEmpty() && (schemaContext == null || schemaContext.schema() == null)) {
            throw new IllegalArgumentException("plan.schema.unavailable");
        }
        List<GraphPlan> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (GraphRequest request : requests) {
            String requestId = normalize(request == null ? null : request.id());
            if (request == null || requestId.isEmpty() || requestId.length() > 80 || !ids.add(requestId)) {
                throw new IllegalArgumentException("plan.graph-request.id");
            }
            GraphPlan graphPlan = toGraphPlan(request);
            ValidationResult validation = graphPlanValidationService.validate(
                schemaContext,
                graphPlan,
                settings.candidateLimit(),
                settings.followUpMinimumRemaining()
            );
            if (!validation.valid()) {
                String category = validation.errors().isEmpty() ? "unknown" : validation.errors().getFirst();
                throw new IllegalArgumentException("plan.graph-request." + category);
            }
            result.add(graphPlan);
        }
        return List.copyOf(result);
    }

    private GraphPlan toGraphPlan(GraphRequest request) {
        List<ComparisonFilter> filters = request.filters().stream().map(this::toFilter).toList();
        List<TypedHop> hops = request.hops().stream()
            .map(value -> new TypedHop(value.relationshipType(), value.targetLabel(), value.direction()))
            .toList();
        List<PropertyProjection> projections = request.projections().stream().map(this::toProjection).toList();
        return new GraphPlan(request.rootLabel(), List.copyOf(filters), hops, List.copyOf(projections), null, List.of(), request.limit());
    }

    private ComparisonFilter toFilter(GraphFilter filter) {
        if (filter == null || filter.operator() == null) {
            throw new IllegalArgumentException("plan.graph-filter.invalid");
        }
        return new ComparisonFilter(
            new PropertyReference(filter.nodeIndex(), filter.property()),
            filter.operator(),
            literal(filter.literal())
        );
    }

    private PropertyProjection toProjection(GraphProjection projection) {
        if (projection == null) {
            throw new IllegalArgumentException("plan.graph-projection.invalid");
        }
        return new PropertyProjection(
            new PropertyReference(projection.nodeIndex(), projection.property()),
            projection.alias()
        );
    }

    private Literal literal(LiteralValue literal) {
        if (literal == null || literal.type() == null) {
            throw new IllegalArgumentException("plan.graph-literal.invalid");
        }
        return switch (literal.type()) {
            case STRING -> new StringLiteral(requireLiteral(literal.value()));
            case LONG -> new LongLiteral(Long.valueOf(requireLiteral(literal.value())));
            case DOUBLE -> new DoubleLiteral(Double.valueOf(requireLiteral(literal.value())));
            case BOOLEAN -> new BooleanLiteral(parseBoolean(requireLiteral(literal.value())));
            case STRING_LIST -> new ListLiteral(literal.values().stream().map(StringLiteral::new).map(Literal.class::cast).toList());
            case LONG_LIST -> new ListLiteral(literal.values().stream().map(Long::valueOf).map(LongLiteral::new).map(Literal.class::cast).toList());
            case DOUBLE_LIST -> new ListLiteral(literal.values().stream().map(Double::valueOf).map(DoubleLiteral::new).map(Literal.class::cast).toList());
            case BOOLEAN_LIST -> new ListLiteral(literal.values().stream().map(this::parseBoolean).map(BooleanLiteral::new).map(Literal.class::cast).toList());
        };
    }

    private Boolean parseBoolean(String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("plan.graph-literal.boolean");
        }
        return Boolean.valueOf(value);
    }

    private String requireLiteral(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("plan.graph-literal.empty");
        }
        return normalized;
    }

    private PlanSummary summary(Plan plan, MetadataConstraints metadata, boolean fallback, String category) {
        return new PlanSummary(
            plan.version(),
            io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.PLANNER_PROMPT_REVISION,
            plan.subquestions().size(),
            plan.exactTerms().size(),
            plan.graphRequests().size(),
            metadata.present(),
            fallback,
            sanitize(category)
        );
    }

    private String optional(String value, int maximumLength) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("plan.metadata.bound");
        }
        return normalized;
    }

    private String bounded(String value, int maximumLength) {
        return value.substring(0, Math.min(maximumLength, value.length()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.substring(0, Math.min(80, sanitized.length()));
    }

    public record ValidatedPlan(
        List<Subquery> subqueries,
        List<String> exactTerms,
        MetadataConstraints metadata,
        List<GraphPlan> graphPlans,
        PlanSummary summary
    ) {
        public ValidatedPlan {
            subqueries = List.copyOf(subqueries);
            exactTerms = List.copyOf(exactTerms);
            graphPlans = List.copyOf(graphPlans);
        }
    }
}
