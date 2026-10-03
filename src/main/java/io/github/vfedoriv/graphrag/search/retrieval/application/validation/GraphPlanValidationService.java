package io.github.vfedoriv.graphrag.search.retrieval.application.validation;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts;

import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess;

import io.github.vfedoriv.graphrag.search.runs.ports.SearchSchemas;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Aggregation;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.AggregationFunction;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.BooleanLiteral;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.ComparisonFilter;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.ComparisonOperator;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Direction;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.DoubleLiteral;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Filter;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.GraphPlan;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.JunctionFilter;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.ListLiteral;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Literal;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.LongLiteral;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.NodeProjection;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Ordering;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Projection;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.PropertyProjection;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.PropertyReference;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.RelationshipProjection;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.StringLiteral;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.TypedHop;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.time.Duration;
import io.github.vfedoriv.graphrag.search.retrieval.domain.GraphPlanValidation.ValidatedGraphPlan;
import io.github.vfedoriv.graphrag.search.retrieval.domain.GraphPlanValidation.ValidationResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class GraphPlanValidationService {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final SearchSchemas activeSchemaResolver;
    private final RuntimeSettingsAccess runtimeSettingsService;

    public GraphPlanValidationService(
        SearchSchemas activeSchemaResolver,
        RuntimeSettingsAccess runtimeSettingsService
    ) {
        this.activeSchemaResolver = activeSchemaResolver;
        this.runtimeSettingsService = runtimeSettingsService;
    }

    public ValidationResult validate(String knowledgeBaseId, GraphPlan plan) {
        List<String> errors = new ArrayList<>();
        if (plan == null) {
            return new ValidationResult(false, List.of("plan.required"), null);
        }
        SchemaSnapshot context;
        RuntimeSettingsAccess.QuerySettings settings;
        try {
            context = activeSchemaResolver.resolveActive(knowledgeBaseId);
            settings = runtimeSettingsService.query();
        } catch (RuntimeException exception) {
            return new ValidationResult(false, List.of("scope.active-schema-unavailable"), null);
        }
        validatePlan(plan, context.schema(), settings.maxRows(), errors);
        if (!errors.isEmpty()) {
            return new ValidationResult(false, List.copyOf(errors), null);
        }
        return new ValidationResult(
            true,
            List.of(),
            new ValidatedGraphPlan(plan, context, settings.maxRows(), Duration.ofSeconds(settings.timeoutSeconds()))
        );
    }

    public ValidationResult validate(
        SchemaSnapshot context,
        GraphPlan plan,
        int maxRows,
        Duration timeout
    ) {
        if (context == null || context.schema() == null) {
            return new ValidationResult(false, List.of("scope.schema-snapshot-unavailable"), null);
        }
        if (maxRows < 1 || timeout == null || timeout.isNegative() || timeout.isZero()) {
            return new ValidationResult(false, List.of("scope.policy-invalid"), null);
        }
        List<String> errors = new ArrayList<>();
        if (plan == null) {
            return new ValidationResult(false, List.of("plan.required"), null);
        }
        validatePlan(plan, context.schema(), maxRows, errors);
        if (!errors.isEmpty()) {
            return new ValidationResult(false, List.copyOf(errors), null);
        }
        return new ValidationResult(true, List.of(), new ValidatedGraphPlan(plan, context, maxRows, timeout));
    }

    private void validatePlan(
        GraphPlan plan,
        SchemaDocument schema,
        int maxRows,
        List<String> errors
    ) {
        List<String> labels = new ArrayList<>();
        SchemaDocument.NodeDefinition root = node(schema, plan.rootLabel());
        if (!identifier(plan.rootLabel()) || root == null) {
            errors.add("root-label.unknown");
            return;
        }
        labels.add(root.label());
        if (plan.hops().size() > io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.MAX_HOPS) {
            errors.add("hops.limit-exceeded");
        }
        validateHops(plan.hops(), schema, labels, errors);
        for (Filter filter : plan.filters()) {
            validateFilter(filter, labels, schema, errors);
        }
        validateProjections(plan.projections(), plan.hops().size(), labels, schema, errors);
        validateAggregation(plan.aggregation(), labels, schema, errors);
        validateOrdering(plan.ordering(), plan.projections(), plan.aggregation(), errors);
        if (plan.limit() < 1 || plan.limit() > maxRows) {
            errors.add("limit.out-of-range");
        }
    }

    private void validateHops(
        List<TypedHop> hops,
        SchemaDocument schema,
        List<String> labels,
        List<String> errors
    ) {
        String currentLabel = labels.get(0);
        int boundedSize = Math.min(hops.size(), io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.MAX_HOPS);
        for (int index = 0; index < boundedSize; index++) {
            TypedHop hop = hops.get(index);
            if (hop == null || !identifier(hop.relationshipType()) || !identifier(hop.targetLabel())
                || hop.direction() == null) {
                errors.add("hop.invalid");
                continue;
            }
            SchemaDocument.RelationshipDefinition relationship = relationship(schema, hop.relationshipType());
            SchemaDocument.NodeDefinition target = node(schema, hop.targetLabel());
            if (relationship == null || target == null) {
                errors.add("hop.schema-mismatch");
                continue;
            }
            boolean endpointsMatch = hop.direction() == Direction.OUTGOING
                ? currentLabel.equals(relationship.from()) && hop.targetLabel().equals(relationship.to())
                : currentLabel.equals(relationship.to()) && hop.targetLabel().equals(relationship.from());
            if (!endpointsMatch) {
                errors.add("hop.endpoints-mismatch");
                continue;
            }
            currentLabel = target.label();
            labels.add(currentLabel);
        }
    }

    private void validateFilter(
        Filter filter,
        List<String> labels,
        SchemaDocument schema,
        List<String> errors
    ) {
        if (filter instanceof ComparisonFilter comparison) {
            SchemaDocument.PropertyDefinition property = property(comparison.property(), labels, schema);
            if (property == null) {
                errors.add("filter.property-unknown");
                return;
            }
            if (comparison.operator() == null || comparison.literal() == null) {
                errors.add("filter.operation-invalid");
                return;
            }
            validateOperatorAndLiteral(property.type(), comparison.operator(), comparison.literal(), errors);
            return;
        }
        if (filter instanceof JunctionFilter junction) {
            if (junction.operator() == null || junction.filters().size() < 2) {
                errors.add("filter.junction-invalid");
                return;
            }
            for (Filter child : junction.filters()) {
                validateFilter(child, labels, schema, errors);
            }
            return;
        }
        errors.add("filter.variant-unsupported");
    }

    private void validateOperatorAndLiteral(
        String schemaType,
        ComparisonOperator operator,
        Literal literal,
        List<String> errors
    ) {
        String normalizedType = schemaType == null ? "" : schemaType.toLowerCase(Locale.ROOT);
        if (operator == ComparisonOperator.IN) {
            if (!(literal instanceof ListLiteral list) || list.values().isEmpty()
                || list.values().stream().anyMatch(item -> !literalMatches(normalizedType, item))) {
                errors.add("filter.literal-type-mismatch");
            }
            return;
        }
        if (!literalMatches(normalizedType, literal)) {
            errors.add("filter.literal-type-mismatch");
        }
        if ((operator == ComparisonOperator.CONTAINS || operator == ComparisonOperator.STARTS_WITH)
            && !normalizedType.equals("string")) {
            errors.add("filter.operator-type-mismatch");
        }
        if ((operator == ComparisonOperator.GREATER_THAN
            || operator == ComparisonOperator.GREATER_THAN_OR_EQUAL
            || operator == ComparisonOperator.LESS_THAN
            || operator == ComparisonOperator.LESS_THAN_OR_EQUAL)
            && !(numeric(normalizedType) || temporal(normalizedType))) {
            errors.add("filter.operator-type-mismatch");
        }
    }

    private void validateProjections(
        List<Projection> projections,
        int hopCount,
        List<String> labels,
        SchemaDocument schema,
        List<String> errors
    ) {
        if (projections.isEmpty()) {
            errors.add("projection.required");
            return;
        }
        Set<String> aliases = new HashSet<>();
        for (Projection projection : projections) {
            if (projection == null || !identifier(projection.alias()) || !aliases.add(projection.alias())) {
                errors.add("projection.alias-invalid");
                continue;
            }
            if (projection instanceof NodeProjection nodeProjection) {
                if (nodeProjection.nodeIndex() < 0 || nodeProjection.nodeIndex() >= labels.size()) {
                    errors.add("projection.node-index-invalid");
                }
            } else if (projection instanceof RelationshipProjection relationshipProjection) {
                if (relationshipProjection.hopIndex() < 0 || relationshipProjection.hopIndex() >= hopCount) {
                    errors.add("projection.hop-index-invalid");
                }
            } else if (projection instanceof PropertyProjection propertyProjection) {
                if (property(propertyProjection.property(), labels, schema) == null) {
                    errors.add("projection.property-unknown");
                }
            } else {
                errors.add("projection.variant-unsupported");
            }
        }
    }

    private void validateAggregation(
        Aggregation aggregation,
        List<String> labels,
        SchemaDocument schema,
        List<String> errors
    ) {
        if (aggregation == null) {
            return;
        }
        if (aggregation.function() == null || !identifier(aggregation.alias())) {
            errors.add("aggregation.invalid");
            return;
        }
        if (aggregation.function() == AggregationFunction.COUNT && aggregation.property() == null) {
            return;
        }
        SchemaDocument.PropertyDefinition property = property(aggregation.property(), labels, schema);
        if (property == null) {
            errors.add("aggregation.property-unknown");
            return;
        }
        if ((aggregation.function() == AggregationFunction.SUM || aggregation.function() == AggregationFunction.AVERAGE)
            && !numeric(property.type().toLowerCase(Locale.ROOT))) {
            errors.add("aggregation.property-type-mismatch");
        }
    }

    private void validateOrdering(
        List<Ordering> ordering,
        List<Projection> projections,
        Aggregation aggregation,
        List<String> errors
    ) {
        Set<String> aliases = new HashSet<>();
        projections.forEach(projection -> {
            if (projection != null) {
                aliases.add(projection.alias());
            }
        });
        if (aggregation != null) {
            if (!aliases.add(aggregation.alias())) {
                errors.add("aggregation.alias-conflict");
            }
        }
        for (Ordering order : ordering) {
            if (order == null || order.direction() == null || !aliases.contains(order.projectionAlias())) {
                errors.add("ordering.projection-unknown");
            }
        }
    }

    private SchemaDocument.PropertyDefinition property(
        PropertyReference reference,
        List<String> labels,
        SchemaDocument schema
    ) {
        if (reference == null || reference.nodeIndex() < 0 || reference.nodeIndex() >= labels.size()
            || !identifier(reference.property())) {
            return null;
        }
        SchemaDocument.NodeDefinition node = node(schema, labels.get(reference.nodeIndex()));
        if (node == null || node.properties() == null) {
            return null;
        }
        return node.properties().stream()
            .filter(candidate -> reference.property().equals(candidate.name()))
            .findFirst()
            .orElse(null);
    }

    private SchemaDocument.NodeDefinition node(SchemaDocument schema, String label) {
        if (schema.nodes() == null) {
            return null;
        }
        return schema.nodes().stream().filter(candidate -> candidate.label().equals(label)).findFirst().orElse(null);
    }

    private SchemaDocument.RelationshipDefinition relationship(SchemaDocument schema, String type) {
        if (schema.relationships() == null) {
            return null;
        }
        return schema.relationships().stream()
            .filter(candidate -> candidate.type().equals(type))
            .findFirst()
            .orElse(null);
    }

    private boolean literalMatches(String schemaType, Literal literal) {
        if (literal == null || literal.value() == null) {
            return false;
        }
        return switch (schemaType) {
            case "string", "date", "datetime", "localdatetime", "time" -> literal instanceof StringLiteral;
            case "integer", "long" -> literal instanceof LongLiteral;
            case "float", "double", "number" -> literal instanceof LongLiteral || literal instanceof DoubleLiteral;
            case "boolean" -> literal instanceof BooleanLiteral;
            default -> false;
        };
    }

    private boolean numeric(String type) {
        return Set.of("integer", "long", "float", "double", "number").contains(type);
    }

    private boolean temporal(String type) {
        return Set.of("date", "datetime", "localdatetime", "time").contains(type);
    }

    private boolean identifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

}
