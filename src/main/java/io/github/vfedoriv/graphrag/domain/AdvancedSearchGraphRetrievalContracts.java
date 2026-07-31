package io.github.vfedoriv.graphrag.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdvancedSearchGraphRetrievalContracts {

    public static final int MAX_HOPS = 2;

    private AdvancedSearchGraphRetrievalContracts() { }

    public enum ComparisonOperator {
        EQUALS,
        NOT_EQUALS,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        IN,
        CONTAINS,
        STARTS_WITH
    }

    public enum LogicalOperator {
        AND,
        OR
    }

    public enum Direction {
        OUTGOING,
        INCOMING
    }

    public enum SortDirection {
        ASCENDING,
        DESCENDING
    }

    public enum AggregationFunction {
        COUNT,
        COUNT_DISTINCT,
        SUM,
        AVERAGE,
        MINIMUM,
        MAXIMUM
    }

    public enum FactKind {
        NODE,
        RELATIONSHIP
    }

    public enum Status {
        COMPLETED,
        VALIDATION_FAILED,
        FAILED,
        DEADLINE_EXCEEDED
    }

    public sealed interface Literal permits StringLiteral, LongLiteral, DoubleLiteral, BooleanLiteral, ListLiteral {
        Object value();
    }

    public record StringLiteral(String value) implements Literal { }

    public record LongLiteral(Long value) implements Literal { }

    public record DoubleLiteral(Double value) implements Literal { }

    public record BooleanLiteral(Boolean value) implements Literal { }

    public record ListLiteral(List<Literal> values) implements Literal {
        public ListLiteral {
            values = values == null ? List.of() : List.copyOf(values);
        }

        @Override
        public Object value() {
            return values.stream().map(Literal::value).toList();
        }
    }

    public record PropertyReference(int nodeIndex, String property) { }

    public sealed interface Filter permits ComparisonFilter, JunctionFilter { }

    public record ComparisonFilter(
        PropertyReference property,
        ComparisonOperator operator,
        Literal literal
    ) implements Filter { }

    public record JunctionFilter(LogicalOperator operator, List<Filter> filters) implements Filter {
        public JunctionFilter {
            filters = filters == null ? List.of() : List.copyOf(filters);
        }
    }

    public record TypedHop(String relationshipType, String targetLabel, Direction direction) { }

    public sealed interface Projection permits NodeProjection, RelationshipProjection, PropertyProjection {
        String alias();
    }

    public record NodeProjection(int nodeIndex, String alias) implements Projection { }

    public record RelationshipProjection(int hopIndex, String alias) implements Projection { }

    public record PropertyProjection(PropertyReference property, String alias) implements Projection { }

    public record Aggregation(
        AggregationFunction function,
        PropertyReference property,
        String alias
    ) { }

    public record Ordering(String projectionAlias, SortDirection direction) { }

    public record GraphPlan(
        String rootLabel,
        List<Filter> filters,
        List<TypedHop> hops,
        List<Projection> projections,
        Aggregation aggregation,
        List<Ordering> ordering,
        int limit
    ) {
        public GraphPlan {
            filters = filters == null ? List.of() : List.copyOf(filters);
            hops = hops == null ? List.of() : List.copyOf(hops);
            projections = projections == null ? List.of() : List.copyOf(projections);
            ordering = ordering == null ? List.of() : List.copyOf(ordering);
        }
    }

    public record Request(String knowledgeBaseId, GraphPlan plan, Instant deadline) {
        public Request {
            if (!hasText(knowledgeBaseId)) {
                throw new IllegalArgumentException("knowledgeBaseId must not be blank");
            }
            if (plan == null) {
                throw new IllegalArgumentException("plan must not be null");
            }
            if (deadline == null) {
                throw new IllegalArgumentException("deadline must not be null");
            }
        }

        public boolean expired() {
            return !Instant.now().isBefore(deadline);
        }

        public Duration remaining() {
            Duration remaining = Duration.between(Instant.now(), deadline);
            return remaining.isNegative() ? Duration.ZERO : remaining;
        }
    }

    public record SchemaRepresentation(
        FactKind kind,
        String type,
        String fromLabel,
        String toLabel
    ) { }

    public record ParentCitation(
        String chunkId,
        String documentId,
        Integer sourceStart,
        Integer sourceEnd,
        Integer pageStart,
        Integer pageEnd,
        String processingRunId,
        String effectiveChunkerRevision,
        String structuralPath
    ) { }

    public record GraphFact(
        String canonicalFactId,
        SchemaRepresentation schema,
        Map<String, Object> properties,
        List<String> evidenceIds,
        List<ParentCitation> citations
    ) {
        public GraphFact {
            properties = immutableMap(properties);
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    public record Row(Map<String, Object> values, List<GraphFact> facts) {
        public Row {
            values = immutableMap(values);
            facts = facts == null ? List.of() : List.copyOf(facts);
        }
    }

    public record Diagnostics(Status status, long latencyMs, int rowCount, String failureCategory) {
        public Diagnostics {
            if (latencyMs < 0 || rowCount < 0) {
                throw new IllegalArgumentException("diagnostic counts must not be negative");
            }
            failureCategory = sanitizeCategory(failureCategory);
        }
    }

    public record Result(List<Row> rows, Diagnostics diagnostics) {
        public Result {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String sanitizeCategory(String value) {
        if (!hasText(value)) {
            return null;
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.substring(0, Math.min(80, sanitized.length()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
