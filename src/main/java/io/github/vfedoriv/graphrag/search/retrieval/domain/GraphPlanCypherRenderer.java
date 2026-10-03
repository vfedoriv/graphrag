package io.github.vfedoriv.graphrag.search.retrieval.domain;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Aggregation;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.AggregationFunction;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.ComparisonFilter;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.ComparisonOperator;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Direction;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Filter;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.GraphPlan;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.JunctionFilter;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.NodeProjection;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Ordering;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Projection;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.PropertyProjection;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.PropertyReference;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.RelationshipProjection;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.SortDirection;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.TypedHop;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.search.retrieval.domain.GraphPlanValidation.ValidatedGraphPlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

@Component
public class GraphPlanCypherRenderer {

    public GraphQuery render(ValidatedGraphPlan validated) {
        GraphPlan plan = validated.plan();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("knowledgeBaseId", validated.schemaContext().knowledgeBaseId());
        parameters.put("schemaId", validated.schemaContext().schemaDefinitionId());
        parameters.put("limit", plan.limit());
        parameters.put("scanLimit", validated.maxRows());

        StringBuilder cypher = new StringBuilder();
        cypher.append("MATCH path = ").append(pathPattern(plan)).append('\n');
        if (!plan.filters().isEmpty()) {
            List<String> filters = new ArrayList<>();
            for (Filter filter : plan.filters()) {
                filters.add(renderFilter(filter, parameters));
            }
            cypher.append("WHERE ").append(String.join(" AND ", filters)).append('\n');
        }
        List<String> variables = variables(plan);
        List<String> supportAliases = new ArrayList<>();
        List<String> supportExpressions = new ArrayList<>();
        List<String> factExpressions = new ArrayList<>();
        appendNodeFacts(plan, supportAliases, supportExpressions, factExpressions);
        appendRelationshipFacts(plan, supportAliases, supportExpressions, factExpressions);

        cypher.append("WITH ").append(String.join(", ", variables));
        for (int index = 0; index < supportExpressions.size(); index++) {
            cypher.append(", ").append(supportExpressions.get(index)).append(" AS ").append(supportAliases.get(index));
        }
        cypher.append('\n');
        cypher.append("WHERE all(support IN [")
            .append(String.join(", ", supportAliases))
            .append("] WHERE size(support) > 0)\n");
        String facts = "[" + String.join(", ", factExpressions) + "]";
        if (plan.aggregation() == null) {
            renderRows(cypher, plan, facts);
        } else {
            renderAggregation(cypher, plan, facts);
        }
        return new GraphQuery(cypher.toString(), parameters);
    }

    private String pathPattern(GraphPlan plan) {
        StringBuilder pattern = new StringBuilder("(n0:").append(token(plan.rootLabel())).append(')');
        for (int index = 0; index < plan.hops().size(); index++) {
            TypedHop hop = plan.hops().get(index);
            String relationship = "[r" + index + ":" + token(hop.relationshipType()) + "]";
            if (hop.direction() == Direction.OUTGOING) {
                pattern.append('-').append(relationship).append("->");
            } else {
                pattern.append("<-").append(relationship).append('-');
            }
            pattern.append("(n").append(index + 1).append(':').append(token(hop.targetLabel())).append(')');
        }
        return pattern.toString();
    }

    private String renderFilter(Filter filter, Map<String, Object> parameters) {
        if (filter instanceof ComparisonFilter comparison) {
            String parameter = "literal" + parameters.size();
            parameters.put(parameter, comparison.literal().value());
            return property(comparison.property()) + " " + operator(comparison.operator()) + " $" + parameter;
        }
        JunctionFilter junction = (JunctionFilter) filter;
        List<String> children = junction.filters().stream()
            .map(child -> renderFilter(child, parameters))
            .toList();
        return "(" + String.join(" " + junction.operator().name() + " ", children) + ")";
    }

    private String operator(ComparisonOperator operator) {
        return switch (operator) {
            case EQUALS -> "=";
            case NOT_EQUALS -> "<>";
            case GREATER_THAN -> ">";
            case GREATER_THAN_OR_EQUAL -> ">=";
            case LESS_THAN -> "<";
            case LESS_THAN_OR_EQUAL -> "<=";
            case IN -> "IN";
            case CONTAINS -> "CONTAINS";
            case STARTS_WITH -> "STARTS WITH";
        };
    }

    private List<String> variables(GraphPlan plan) {
        List<String> variables = new ArrayList<>();
        for (int index = 0; index <= plan.hops().size(); index++) {
            variables.add("n" + index);
        }
        for (int index = 0; index < plan.hops().size(); index++) {
            variables.add("r" + index);
        }
        return variables;
    }

    private void appendNodeFacts(
        GraphPlan plan,
        List<String> supportAliases,
        List<String> supportExpressions,
        List<String> factExpressions
    ) {
        List<String> labels = new ArrayList<>();
        labels.add(plan.rootLabel());
        plan.hops().forEach(hop -> labels.add(hop.targetLabel()));
        for (int index = 0; index < labels.size(); index++) {
            String variable = "n" + index;
            String support = "supportN" + index;
            supportAliases.add(support);
            supportExpressions.add(supportExpression(
                variable,
                "NODE",
                support,
                "-[:ASSERTS_NODE]->(" + variable + ")",
                ""
            ));
            factExpressions.add("{canonicalFactId: " + variable + ".id, factKind: 'NODE', schemaType: '"
                + labels.get(index) + "', fromLabel: null, toLabel: null, properties: properties("
                + variable + "), supports: " + support + "}");
        }
    }

    private void appendRelationshipFacts(
        GraphPlan plan,
        List<String> supportAliases,
        List<String> supportExpressions,
        List<String> factExpressions
    ) {
        List<String> labels = new ArrayList<>();
        labels.add(plan.rootLabel());
        plan.hops().forEach(hop -> labels.add(hop.targetLabel()));
        for (int index = 0; index < plan.hops().size(); index++) {
            TypedHop hop = plan.hops().get(index);
            String variable = "r" + index;
            String support = "supportR" + index;
            supportAliases.add(support);
            String fromVariable = hop.direction() == Direction.OUTGOING ? "n" + index : "n" + (index + 1);
            String toVariable = hop.direction() == Direction.OUTGOING ? "n" + (index + 1) : "n" + index;
            supportExpressions.add(supportExpression(
                variable,
                "RELATIONSHIP",
                support,
                "-[:ASSERTS_FROM]->(" + fromVariable + ")",
                " AND EXISTS { MATCH (" + supportEvidence(support) + ")-[:ASSERTS_TO]->(" + toVariable + ") }"
            ));
            String from = hop.direction() == Direction.OUTGOING ? labels.get(index) : labels.get(index + 1);
            String to = hop.direction() == Direction.OUTGOING ? labels.get(index + 1) : labels.get(index);
            factExpressions.add("{canonicalFactId: " + variable
                + ".id, factKind: 'RELATIONSHIP', schemaType: '" + hop.relationshipType()
                + "', fromLabel: '" + from + "', toLabel: '" + to + "', properties: properties("
                + variable + "), supports: " + support + "}");
        }
    }

    private String supportExpression(
        String factVariable,
        String factKind,
        String alias,
        String assertionPattern,
        String assertionPredicate
    ) {
        String suffix = alias.substring(alias.length() - 1);
        String chunk = "c" + alias.charAt(7) + suffix;
        String evidence = "e" + alias.charAt(7) + suffix;
        return "[(" + chunk + ":DocumentChunk)-[:HAS_GRAPH_EVIDENCE]->("
            + evidence + ":GraphExtractionEvidence)" + assertionPattern + " WHERE "
            + evidence + ".canonicalFactId = " + factVariable + ".id"
            + " AND " + evidence + ".factKind = '" + factKind + "'"
            + " AND " + evidence + ".knowledgeBaseId = $knowledgeBaseId"
            + " AND " + evidence + ".schemaId = $schemaId"
            + " AND " + chunk + ".id = " + evidence + ".sourceChunkId"
            + " AND " + chunk + ".knowledgeBaseId = $knowledgeBaseId"
            + " AND " + chunk + ".documentId = " + evidence + ".sourceDocumentId"
            + " AND " + chunk + ".kind = 'PARENT'"
            + " AND " + chunk + ".processingRunId = " + evidence + ".processingRunId"
            + assertionPredicate
            + " | {evidenceId: " + evidence + ".id, chunkId: " + chunk + ".id, documentId: "
            + chunk + ".documentId, chunkKind: " + chunk + ".kind, sourceStart: " + chunk + ".sourceStart, sourceEnd: "
            + chunk + ".sourceEnd, pageStart: " + chunk + ".pageStart, pageEnd: " + chunk
            + ".pageEnd, processingRunId: " + chunk + ".processingRunId, effectiveChunkerRevision: "
            + chunk + ".effectiveChunkerRevision, structuralPath: " + chunk + ".structuralPath}]";
    }

    private String supportEvidence(String alias) {
        String suffix = alias.substring(alias.length() - 1);
        return "e" + alias.charAt(7) + suffix;
    }

    private void renderRows(StringBuilder cypher, GraphPlan plan, String facts) {
        cypher.append("WITH ").append(valuesMap(plan.projections(), null)).append(" AS values, ")
            .append(facts).append(" AS facts\n")
            .append("RETURN values, facts\n");
        appendOrdering(cypher, plan.ordering(), "values.");
        cypher.append("LIMIT $limit\n");
    }

    private void renderAggregation(StringBuilder cypher, GraphPlan plan, String facts) {
        cypher.append("WITH ").append(String.join(", ", variables(plan))).append(", ")
            .append(facts).append(" AS facts\n")
            .append("LIMIT $scanLimit\n");
        List<String> groupItems = new ArrayList<>();
        for (Projection projection : plan.projections()) {
            groupItems.add(projectionExpression(projection) + " AS " + token(projection.alias()));
        }
        Aggregation aggregation = plan.aggregation();
        groupItems.add(aggregationExpression(aggregation) + " AS " + token(aggregation.alias()));
        groupItems.add("collect(facts) AS factGroups");
        cypher.append("WITH ").append(String.join(", ", groupItems)).append('\n');
        cypher.append("RETURN ").append(valuesMap(plan.projections(), aggregation))
            .append(" AS values, reduce(allFacts = [], groupFacts IN factGroups | allFacts + groupFacts) AS facts\n");
        appendOrdering(cypher, plan.ordering(), "");
        cypher.append("LIMIT $limit\n");
    }

    private String valuesMap(List<Projection> projections, Aggregation aggregation) {
        StringJoiner values = new StringJoiner(", ", "{", "}");
        for (Projection projection : projections) {
            String expression = aggregation == null ? projectionExpression(projection) : token(projection.alias());
            values.add(token(projection.alias()) + ": " + expression);
        }
        if (aggregation != null) {
            values.add(token(aggregation.alias()) + ": " + token(aggregation.alias()));
        }
        return values.toString();
    }

    private String projectionExpression(Projection projection) {
        if (projection instanceof NodeProjection node) {
            return "properties(n" + node.nodeIndex() + ")";
        }
        if (projection instanceof RelationshipProjection relationship) {
            return "properties(r" + relationship.hopIndex() + ")";
        }
        PropertyProjection propertyProjection = (PropertyProjection) projection;
        return property(propertyProjection.property());
    }

    private String aggregationExpression(Aggregation aggregation) {
        String expression = aggregation.property() == null ? "*" : property(aggregation.property());
        return switch (aggregation.function()) {
            case COUNT -> "count(" + expression + ")";
            case COUNT_DISTINCT -> "count(DISTINCT " + expression + ")";
            case SUM -> "sum(" + expression + ")";
            case AVERAGE -> "avg(" + expression + ")";
            case MINIMUM -> "min(" + expression + ")";
            case MAXIMUM -> "max(" + expression + ")";
        };
    }

    private void appendOrdering(StringBuilder cypher, List<Ordering> ordering, String prefix) {
        if (ordering.isEmpty()) {
            return;
        }
        List<String> expressions = ordering.stream()
            .map(order -> prefix + token(order.projectionAlias()) + " "
                + (order.direction() == SortDirection.ASCENDING ? "ASC" : "DESC"))
            .toList();
        cypher.append("ORDER BY ").append(String.join(", ", expressions)).append('\n');
    }

    private String property(PropertyReference reference) {
        return "n" + reference.nodeIndex() + "." + token(reference.property());
    }

    private String token(String identifier) {
        return "`" + identifier + "`";
    }
}
