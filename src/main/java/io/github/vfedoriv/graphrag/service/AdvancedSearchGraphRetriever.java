package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.schemas.registry.application.ActiveSchemaContext;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Diagnostics;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.FactKind;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.GraphFact;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.ParentCitation;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Row;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.SchemaRepresentation;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.error.QueryDeadlineExceededException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.repository.GraphRetrievalRepository;
import io.github.vfedoriv.graphrag.repository.GraphRetrievalRepository.Query;
import io.github.vfedoriv.graphrag.service.GraphPlanValidationService.ValidatedGraphPlan;
import io.github.vfedoriv.graphrag.service.GraphPlanValidationService.ValidationResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AdvancedSearchGraphRetriever {

    private final GraphPlanValidationService validationService;
    private final GraphPlanCypherRenderer renderer;
    private final GraphRetrievalRepository repository;

    public AdvancedSearchGraphRetriever(
        GraphPlanValidationService validationService,
        GraphPlanCypherRenderer renderer,
        GraphRetrievalRepository repository
    ) {
        this.validationService = validationService;
        this.renderer = renderer;
        this.repository = repository;
    }

    public Result retrieve(Request request) {
        return retrieve(request, null, 0, null);
    }

    public Result retrieve(
        Request request,
        ActiveSchemaContext schemaContext,
        int maxRows,
        Duration timeout
    ) {
        long startNanos = System.nanoTime();
        if (request.expired()) {
            return failed(Status.DEADLINE_EXCEEDED, startNanos, "deadline.expired");
        }
        ValidationResult validation = schemaContext == null
            ? validationService.validate(request.knowledgeBaseId(), request.plan())
            : validationService.validate(schemaContext, request.plan(), maxRows, timeout);
        if (!validation.valid()) {
            String category = validation.errors().isEmpty() ? "plan.invalid" : validation.errors().getFirst();
            return failed(Status.VALIDATION_FAILED, startNanos, category);
        }
        try {
            ValidatedGraphPlan validatedPlan = validation.validatedPlan();
            Duration effectiveTimeout = shorter(request.remaining(), validatedPlan.timeout());
            if (effectiveTimeout.isZero()) {
                return failed(Status.DEADLINE_EXCEEDED, startNanos, "deadline.expired");
            }
            Query query = renderer.render(validatedPlan);
            List<Row> rows = repository.execute(query, effectiveTimeout).stream()
                .map(this::toRow)
                .filter(row -> !row.facts().isEmpty())
                .limit(request.plan().limit())
                .toList();
            return new Result(
                rows,
                new Diagnostics(Status.COMPLETED, LogMetadata.elapsedMillis(startNanos), rows.size(), null)
            );
        } catch (QueryDeadlineExceededException exception) {
            return failed(Status.DEADLINE_EXCEEDED, startNanos, exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            Status status = request.expired() ? Status.DEADLINE_EXCEEDED : Status.FAILED;
            return failed(status, startNanos, exception.getClass().getSimpleName());
        }
    }

    private Row toRow(Map<String, Object> rawRow) {
        Map<String, Object> values = mapValue(rawRow.get("values"));
        Map<String, MutableFact> facts = new LinkedHashMap<>();
        for (Object rawFact : iterable(rawRow.get("facts"))) {
            Map<String, Object> fact = mapValue(rawFact);
            String canonicalFactId = stringValue(fact.get("canonicalFactId"));
            FactKind kind = factKind(fact.get("factKind"));
            String type = stringValue(fact.get("schemaType"));
            if (!hasText(canonicalFactId) || kind == null || !hasText(type)) {
                continue;
            }
            List<Support> supports = validSupports(fact.get("supports"));
            if (supports.isEmpty()) {
                continue;
            }
            SchemaRepresentation schema = new SchemaRepresentation(
                kind,
                type,
                stringValue(fact.get("fromLabel")),
                stringValue(fact.get("toLabel"))
            );
            MutableFact accumulator = facts.computeIfAbsent(
                canonicalFactId,
                ignored -> new MutableFact(canonicalFactId, schema, mapValue(fact.get("properties")))
            );
            supports.forEach(accumulator::add);
        }
        List<GraphFact> graphFacts = facts.values().stream().map(MutableFact::toGraphFact).toList();
        return new Row(values, graphFacts);
    }

    private List<Support> validSupports(Object rawSupports) {
        List<Support> supports = new ArrayList<>();
        for (Object rawSupport : iterable(rawSupports)) {
            Map<String, Object> support = mapValue(rawSupport);
            String evidenceId = stringValue(support.get("evidenceId"));
            String chunkId = stringValue(support.get("chunkId"));
            String documentId = stringValue(support.get("documentId"));
            String processingRunId = stringValue(support.get("processingRunId"));
            if (!hasText(evidenceId) || !hasText(chunkId) || !hasText(documentId)
                || !hasText(processingRunId) || !"PARENT".equals(stringValue(support.get("chunkKind")))) {
                continue;
            }
            Integer sourceStart = integerValue(support.get("sourceStart"));
            Integer sourceEnd = integerValue(support.get("sourceEnd"));
            Integer pageStart = integerValue(support.get("pageStart"));
            Integer pageEnd = integerValue(support.get("pageEnd"));
            if (!validRange(sourceStart, sourceEnd) || !validRange(pageStart, pageEnd)) {
                continue;
            }
            ParentCitation citation = new ParentCitation(
                chunkId,
                documentId,
                sourceStart,
                sourceEnd,
                pageStart,
                pageEnd,
                processingRunId,
                stringValue(support.get("effectiveChunkerRevision")),
                stringValue(support.get("structuralPath"))
            );
            supports.add(new Support(evidenceId, citation));
        }
        return supports;
    }

    private boolean validRange(Integer start, Integer end) {
        if (start == null && end == null) {
            return true;
        }
        return start != null && end != null && start >= 0 && end >= start;
    }

    private Duration shorter(Duration first, Duration second) {
        return first.compareTo(second) < 0 ? first : second;
    }

    private Result failed(Status status, long startNanos, String category) {
        return new Result(
            List.of(),
            new Diagnostics(status, LogMetadata.elapsedMillis(startNanos), 0, category)
        );
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, item) -> normalized.put(String.valueOf(key), item));
        return normalized;
    }

    private Iterable<?> iterable(Object value) {
        return value instanceof Iterable<?> iterable ? iterable : List.of();
    }

    private FactKind factKind(Object value) {
        try {
            return FactKind.valueOf(stringValue(value));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Support(String evidenceId, ParentCitation citation) { }

    private static final class MutableFact {

        private final String canonicalFactId;
        private final SchemaRepresentation schema;
        private final Map<String, Object> properties;
        private final Set<String> evidenceIds = new LinkedHashSet<>();
        private final Map<String, ParentCitation> citations = new LinkedHashMap<>();

        private MutableFact(
            String canonicalFactId,
            SchemaRepresentation schema,
            Map<String, Object> properties
        ) {
            this.canonicalFactId = canonicalFactId;
            this.schema = schema;
            this.properties = properties;
        }

        private void add(Support support) {
            evidenceIds.add(support.evidenceId());
            citations.putIfAbsent(support.citation().chunkId(), support.citation());
        }

        private GraphFact toGraphFact() {
            return new GraphFact(
                canonicalFactId,
                schema,
                properties,
                List.copyOf(evidenceIds),
                List.copyOf(citations.values())
            );
        }
    }
}
