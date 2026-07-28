package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.Candidate;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.CandidateKind;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.EvidenceOrigin;
import io.github.vfedoriv.graphrag.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.DiffBaselineType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAggregateRevisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftCompatibility;
import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftDecisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftDecisionType;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftReviewState;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.ConflictResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.ConflictListScope;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.CandidatePageResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.CandidateResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.DecisionRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.DecisionResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.DiffItem;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.DiffBaseline;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.DiffResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.ProjectionResponse;
import io.github.vfedoriv.graphrag.dto.SchemaDraftDtos.ResolveConflictRequest;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftAggregateRevisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftConflictRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftDecisionRepository;
import io.github.vfedoriv.graphrag.repository.SchemaDraftRepository;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.schema.SchemaParser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class SchemaDraftReviewService {
    private final SchemaDraftLifecycleService lifecycleService;
    private final SchemaDraftRepository draftRepository;
    private final SchemaDraftAggregateRevisionRepository aggregateRepository;
    private final SchemaDraftDecisionRepository decisionRepository;
    private final SchemaDraftConflictRepository conflictRepository;
    private final SchemaDraftConflictService conflictService;
    private final SchemaDefinitionRepository schemaRepository;
    private final SchemaParser schemaParser;
    private final SchemaDraftJsonSupport jsonSupport;
    private final ObjectMapper objectMapper;

    public SchemaDraftReviewService(
        SchemaDraftLifecycleService lifecycleService,
        SchemaDraftRepository draftRepository,
        SchemaDraftAggregateRevisionRepository aggregateRepository,
        SchemaDraftDecisionRepository decisionRepository,
        SchemaDraftConflictRepository conflictRepository,
        SchemaDraftConflictService conflictService,
        SchemaDefinitionRepository schemaRepository,
        SchemaParser schemaParser,
        SchemaDraftJsonSupport jsonSupport,
        ObjectMapper objectMapper
    ) {
        this.lifecycleService = lifecycleService;
        this.draftRepository = draftRepository;
        this.aggregateRepository = aggregateRepository;
        this.decisionRepository = decisionRepository;
        this.conflictRepository = conflictRepository;
        this.conflictService = conflictService;
        this.schemaRepository = schemaRepository;
        this.schemaParser = schemaParser;
        this.jsonSupport = jsonSupport;
        this.objectMapper = objectMapper;
    }

    @RelationalTransactional(readOnly = true)
    public CandidatePageResponse candidates(String knowledgeBaseId, String draftId, int page, int size) {
        SchemaDraftNode draft = lifecycleService.requireOwned(knowledgeBaseId, draftId);
        List<Candidate> candidates = effectiveCandidates(draft);
        Map<String, SchemaDraftDecisionNode> latest = latestDecisions(draftId);
        List<CandidateResponse> responses = candidates.stream().map(candidate -> {
            SchemaDraftDecisionNode decision = latest.get(candidate.identity());
            return new CandidateResponse(candidate.kind(), candidate.identity(), candidate.label(), candidate.property(),
                candidate.propertyType(), candidate.keys(), candidate.relationshipType(), candidate.fromLabel(),
                candidate.toLabel(), candidate.originalLabel(), candidate.originalProperty(),
                candidate.originalRelationshipType(), candidate.confidence(), candidate.origins(), candidate.evidence(),
                candidate.supportCount(), candidate.reviewState(), decision == null ? null : decision.getReviewState(),
                decision == null ? null : decision.getId());
        }).toList();
        int boundedSize = Math.max(1, Math.min(size, 100));
        int boundedPage = Math.max(0, page);
        int from = (int) Math.min((long) boundedPage * boundedSize, responses.size());
        int to = Math.min(from + boundedSize, responses.size());
        return new CandidatePageResponse(boundedPage, boundedSize, responses.size(), responses.subList(from, to));
    }

    @RelationalTransactional
    public DecisionResponse decide(
        String knowledgeBaseId, String draftId, DecisionRequest request
    ) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, request.revision());
        Map<String, Candidate> candidates = effectiveCandidates(draft).stream()
            .collect(Collectors.toMap(Candidate::identity, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Candidate current = candidates.get(request.candidateIdentity());
        if (current == null && request.type() != SchemaDraftDecisionType.RESOLVE) {
            throw new NotFoundException("Draft candidate not found: " + request.candidateIdentity());
        }
        if ((request.type() == SchemaDraftDecisionType.MODIFY || request.type() == SchemaDraftDecisionType.PIN)
            && request.resultingValue() == null) {
            throw new IllegalArgumentException("Modified or pinned decisions require resultingValue");
        }
        validateResultIdentity(request, current);
        SchemaDraftDecisionNode decision = new SchemaDraftDecisionNode();
        decision.setId(UUID.randomUUID().toString());
        decision.setDraftId(draftId);
        decision.setSequence(decisionRepository.countByDraftId(draftId) + 1);
        decision.setDraftRevision(draft.getRevision());
        decision.setType(request.type());
        decision.setReviewState(reviewState(request.type()));
        decision.setCandidateIdentity(request.candidateIdentity());
        decision.setPriorValueJson(current == null ? null : jsonSupport.canonical(current));
        decision.setResultingValueJson(request.resultingValue() == null ? null : jsonSupport.canonical(request.resultingValue()));
        decision.setRationale(request.rationale());
        decision.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        SchemaDraftDecisionNode saved;
        try {
            saved = decisionRepository.save(decision);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Concurrent schema draft decision; retry with current revision");
        }
        lifecycleService.advance(draft);
        draftRepository.save(draft);
        return toResponse(saved);
    }

    @RelationalTransactional(readOnly = true)
    public List<DecisionResponse> decisions(String knowledgeBaseId, String draftId) {
        lifecycleService.requireOwned(knowledgeBaseId, draftId);
        return decisionRepository.findByDraftIdOrderBySequenceAsc(draftId).stream().map(this::toResponse).toList();
    }

    @RelationalTransactional(readOnly = true)
    public List<ConflictResponse> conflicts(
        String knowledgeBaseId, String draftId, ConflictListScope scope
    ) {
        SchemaDraftNode draft = lifecycleService.requireOwned(knowledgeBaseId, draftId);
        String currentAggregateId = draft.getCurrentAggregateId();
        if (scope == ConflictListScope.CURRENT && currentAggregateId == null) {
            return List.of();
        }
        List<SchemaDraftConflictNode> conflicts = scope == ConflictListScope.ALL
            ? conflictRepository.findHistory(draftId)
            : conflictRepository.findByAggregateRevision(draftId, currentAggregateId);
        return conflicts.stream().map(value -> toResponse(value, currentAggregateId)).toList();
    }

    @RelationalTransactional
    public ConflictResponse resolve(
        String knowledgeBaseId, String draftId, String conflictId, ResolveConflictRequest request
    ) {
        SchemaDraftNode draft = lifecycleService.requireMutable(knowledgeBaseId, draftId, request.revision());
        SchemaDraftConflictNode conflict = conflictRepository.findByIdAndDraftId(conflictId, draftId)
            .orElseThrow(() -> new NotFoundException("Draft conflict not found: " + conflictId));
        if (conflict.isResolved()) {
            throw new ConflictException("Draft conflict is already resolved");
        }
        conflictService.resolve(conflict, request.selectedAlternative(), request.customResolution());
        SchemaDraftConflictNode saved = conflictRepository.save(conflict);
        lifecycleService.advance(draft);
        draftRepository.save(draft);
        return toResponse(saved, draft.getCurrentAggregateId());
    }

    @RelationalTransactional(readOnly = true)
    public ProjectionResponse projection(String knowledgeBaseId, String draftId) {
        SchemaDraftNode draft = lifecycleService.requireOwned(knowledgeBaseId, draftId);
        SchemaDraftAggregateRevisionNode aggregate = currentAggregate(draft);
        List<Candidate> candidates = applyDecisions(draft, effectiveCandidates(draft));
        JsonNode schema = project(draft, candidates);
        boolean unresolvedConflict = conflictRepository.findByAggregateRevision(draftId, aggregate.getId()).stream()
            .anyMatch(value -> !value.isResolved());
        boolean unresolvedGuidance = candidates.stream().anyMatch(value ->
            value.origins().contains(EvidenceOrigin.GUIDED)
                && !value.origins().contains(EvidenceOrigin.OBSERVED)
                && latestDecision(draftId, value.identity()) == null);
        return new ProjectionResponse(aggregate.getId(), draft.getRevision(), toPlain(schema),
            !unresolvedConflict && !unresolvedGuidance);
    }

    @RelationalTransactional(readOnly = true)
    public DiffResponse diff(String knowledgeBaseId, String draftId) {
        SchemaDraftNode draft = lifecycleService.requireOwned(knowledgeBaseId, draftId);
        SchemaDraftAggregateRevisionNode aggregate = currentAggregate(draft);
        ProjectionResponse projection = projection(knowledgeBaseId, draftId);
        BaselineSnapshot baseline = comparisonBaseline(draft, aggregate);
        JsonNode before = jsonSupport.parse(baseline.schemaJson());
        Map<String, JsonNode> oldValues = flatten(before);
        Map<String, JsonNode> newValues = flatten(objectMapper.valueToTree(projection.schema()));
        Set<String> coordinates = new java.util.TreeSet<>();
        coordinates.addAll(oldValues.keySet());
        coordinates.addAll(newValues.keySet());
        List<DiffItem> changes = new ArrayList<>();
        for (String coordinate : coordinates) {
            JsonNode oldValue = oldValues.get(coordinate);
            JsonNode newValue = newValues.get(coordinate);
            if (java.util.Objects.equals(oldValue, newValue)) {
                continue;
            }
            String operation = oldValue == null ? "ADD" : newValue == null ? "REMOVE" : "CHANGE";
            changes.add(new DiffItem(coordinate, classify(coordinate, oldValue, newValue), operation,
                toPlain(oldValue), toPlain(newValue)));
        }
        return new DiffResponse(projection.aggregateRevisionId(), projection.draftRevision(),
            new DiffBaseline(baseline.type(), baseline.id(), baseline.contentHash()), List.copyOf(changes));
    }

    @RelationalTransactional
    public boolean promoteIfRevisionCurrent(String draftId, String aggregateId, long expectedDraftRevision) {
        SchemaDraftNode draft = draftRepository.findById(draftId).orElse(null);
        if (draft == null || draft.getRevision() != expectedDraftRevision) {
            return false;
        }
        SchemaDraftAggregateRevisionNode aggregate = aggregateRepository.findById(aggregateId).orElseThrow();
        if (aggregate.getDiffBaselineType() == null) {
            BaselineSnapshot baseline = promotionBaseline(draft);
            aggregate.setDiffBaselineType(baseline.type());
            aggregate.setDiffBaselineId(baseline.id());
            aggregate.setDiffBaselineSchemaJson(baseline.schemaJson());
            aggregate.setDiffBaselineContentHash(baseline.contentHash());
            aggregateRepository.save(aggregate);
        }
        draft.setCurrentAggregateId(aggregateId);
        draft.setUpdatedAt(Instant.now());
        draftRepository.save(draft);
        return true;
    }

    @RelationalTransactional
    public void reconcileAfterAnalysis(String draftId, String aggregateId) {
        SchemaDraftAggregateRevisionNode aggregate = aggregateRepository.findById(aggregateId).orElseThrow();
        Map<String, Candidate> candidates = readCandidates(aggregate.getCandidatesJson()).stream()
            .collect(Collectors.toMap(Candidate::identity, Function.identity(), (left, right) -> right));
        for (SchemaDraftDecisionNode decision : latestDecisions(draftId).values()) {
            Candidate current = candidates.get(decision.getCandidateIdentity());
            if (current == null && decision.getType() != SchemaDraftDecisionType.REJECT) {
                createReviewConflict(
                    draftId,
                    aggregateId,
                    io.github.vfedoriv.graphrag.domain.SchemaDraftConflictType.COORDINATE,
                    decision.getCandidateIdentity(),
                    List.of("EVIDENCE_REMOVED"),
                    decision.getPriorValueJson()
                );
            } else if (current != null && decision.getReviewState() == SchemaDraftReviewState.PINNED
                && decision.getResultingValueJson() != null
                && !jsonSupport.canonical(current).equals(decision.getResultingValueJson())) {
                createReviewConflict(
                    draftId,
                    aggregateId,
                    io.github.vfedoriv.graphrag.domain.SchemaDraftConflictType.PINNED_DEFINITION,
                    decision.getCandidateIdentity(),
                    List.of(jsonSupport.canonical(current), decision.getResultingValueJson()),
                    jsonSupport.canonical(current.evidence())
                );
            }
        }
    }

    private void createReviewConflict(
        String draftId,
        String aggregateId,
        io.github.vfedoriv.graphrag.domain.SchemaDraftConflictType type,
        String coordinate,
        List<String> alternatives,
        String evidence
    ) {
        boolean exists = conflictRepository.findByAggregateRevision(draftId, aggregateId).stream()
            .anyMatch(value -> type == value.getType() && coordinate.equals(value.getCoordinate()));
        if (exists) {
            return;
        }
        conflictService.create(draftId, aggregateId, type, coordinate, alternatives,
            evidence == null ? List.of() : jsonSupport.parse(evidence));
    }

    private List<Candidate> effectiveCandidates(SchemaDraftNode draft) {
        return effectiveCandidates(draft, currentAggregate(draft));
    }

    private List<Candidate> effectiveCandidates(
        SchemaDraftNode draft, SchemaDraftAggregateRevisionNode aggregate
    ) {
        List<Candidate> values = readCandidates(aggregate.getCandidatesJson());
        Map<String, Candidate> candidates = new TreeMap<>();
        inherited(draft).forEach(value -> candidates.put(value.identity(), value));
        values.forEach(value -> candidates.merge(value.identity(), value, this::mergeOrigin));
        return List.copyOf(candidates.values());
    }

    private Candidate mergeOrigin(Candidate inherited, Candidate observed) {
        Set<EvidenceOrigin> origins = new LinkedHashSet<>(inherited.origins());
        origins.addAll(observed.origins());
        return copy(observed, Set.copyOf(origins));
    }

    private List<Candidate> inherited(SchemaDraftNode draft) {
        if (draft.getBaseSchemaId() == null) return List.of();
        SchemaDefinitionNode base = schemaRepository.findById(draft.getBaseSchemaId()).orElseThrow();
        SchemaDocument schema = schemaParser.parse(base.getContent());
        List<Candidate> values = new ArrayList<>();
        for (SchemaDocument.NodeDefinition node : safe(schema.nodes())) {
            values.add(candidate(CandidateKind.NODE, node.label(), null, null, List.of(), null, null, null));
            for (SchemaDocument.PropertyDefinition property : safe(node.properties())) {
                values.add(candidate(CandidateKind.NODE_PROPERTY, node.label(), property.name(), property.type(),
                    List.of(), null, null, null));
            }
            if (node.key() != null && !node.key().isEmpty()) {
                values.add(candidate(CandidateKind.NODE_KEY, node.label(), null, null, node.key(), null, null, null));
            }
        }
        for (SchemaDocument.RelationshipDefinition relationship : safe(schema.relationships())) {
            values.add(candidate(CandidateKind.RELATIONSHIP, null, null, null, List.of(), relationship.type(),
                relationship.from(), relationship.to()));
            for (SchemaDocument.PropertyDefinition property : safe(relationship.properties())) {
                values.add(candidate(CandidateKind.RELATIONSHIP_PROPERTY, null, property.name(), property.type(),
                    List.of(), relationship.type(), relationship.from(), relationship.to()));
            }
        }
        return values;
    }

    private Candidate candidate(
        CandidateKind kind, String label, String property, String type, List<String> keys,
        String relationship, String from, String to
    ) {
        String identity = DiscoveryContracts.identity(kind, label, property, relationship, from, to);
        return new Candidate(kind, identity, label, property, type, keys, relationship, from, to,
            label, property, relationship, null, Set.of(EvidenceOrigin.EXISTING), List.of(), 0,
            DiscoveryContracts.ReviewState.RECOMMENDED);
    }

    private List<Candidate> applyDecisions(SchemaDraftNode draft, List<Candidate> candidates) {
        Map<String, Candidate> effective = candidates.stream().collect(Collectors.toMap(
            Candidate::identity, Function.identity(), (left, right) -> right, TreeMap::new));
        Map<String, SchemaDraftDecisionNode> latest = latestDecisions(draft.getId());
        for (Map.Entry<String, SchemaDraftDecisionNode> entry : latest.entrySet()) {
            SchemaDraftDecisionNode decision = entry.getValue();
            if (decision.getType() == SchemaDraftDecisionType.REJECT) {
                effective.remove(entry.getKey());
            } else if (decision.getResultingValueJson() != null) {
                Candidate replacement = objectMapper.convertValue(jsonSupport.parse(decision.getResultingValueJson()), Candidate.class);
                if (!entry.getKey().equals(replacement.identity())) {
                    throw new ConflictException("Decision coordinate no longer matches candidate: " + entry.getKey());
                }
                effective.put(entry.getKey(), replacement);
            } else if (!effective.containsKey(entry.getKey()) && decision.getPriorValueJson() != null
                && decision.getType() != SchemaDraftDecisionType.REJECT) {
                Candidate retained = objectMapper.convertValue(jsonSupport.parse(decision.getPriorValueJson()), Candidate.class);
                effective.put(entry.getKey(), retained);
            }
        }
        return List.copyOf(effective.values());
    }

    private JsonNode project(SchemaDraftNode draft, List<Candidate> candidates) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("name", draft.getTargetName());
        schema.put("version", draft.getTargetVersion());
        ArrayNode nodes = schema.putArray("nodes");
        Map<String, List<Candidate>> nodeProperties = candidates.stream()
            .filter(value -> value.kind() == CandidateKind.NODE_PROPERTY).collect(Collectors.groupingBy(Candidate::label));
        Map<String, Candidate> keys = candidates.stream().filter(value -> value.kind() == CandidateKind.NODE_KEY)
            .collect(Collectors.toMap(Candidate::label, Function.identity(), (left, right) -> right));
        candidates.stream().filter(value -> value.kind() == CandidateKind.NODE).sorted(Comparator.comparing(Candidate::label))
            .forEach(value -> {
                ObjectNode node = nodes.addObject();
                node.put("label", value.label());
                Candidate key = keys.get(value.label());
                node.set("key", objectMapper.valueToTree(key == null ? List.of() : key.keys()));
                ArrayNode properties = node.putArray("properties");
                nodeProperties.getOrDefault(value.label(), List.of()).stream().sorted(Comparator.comparing(Candidate::property))
                    .forEach(property -> {
                        ObjectNode item = properties.addObject();
                        item.put("name", property.property());
                        item.put("type", property.propertyType());
                        item.put("required", key != null && key.keys().contains(property.property()));
                    });
            });
        ArrayNode relationships = schema.putArray("relationships");
        Map<String, List<Candidate>> relationshipProperties = candidates.stream()
            .filter(value -> value.kind() == CandidateKind.RELATIONSHIP_PROPERTY)
            .collect(Collectors.groupingBy(value -> relationshipCoordinate(value.relationshipType(), value.fromLabel(), value.toLabel())));
        candidates.stream().filter(value -> value.kind() == CandidateKind.RELATIONSHIP)
            .sorted(Comparator.comparing(Candidate::identity)).forEach(value -> {
                ObjectNode relationship = relationships.addObject();
                relationship.put("type", value.relationshipType());
                relationship.put("from", value.fromLabel());
                relationship.put("to", value.toLabel());
                ArrayNode properties = relationship.putArray("properties");
                relationshipProperties.getOrDefault(relationshipCoordinate(value.relationshipType(), value.fromLabel(), value.toLabel()), List.of())
                    .stream().sorted(Comparator.comparing(Candidate::property)).forEach(property -> {
                        ObjectNode item = properties.addObject();
                        item.put("name", property.property());
                        item.put("type", property.propertyType());
                        item.put("required", false);
                    });
            });
        schema.putArray("indexes");
        schema.putArray("vectorIndexes");
        return schema;
    }

    private BaselineSnapshot promotionBaseline(SchemaDraftNode draft) {
        if (draft.getBaseSchemaId() != null) {
            String schemaJson = canonicalSchema(schemaRepository.findById(draft.getBaseSchemaId()).orElseThrow().getContent());
            return baseline(DiffBaselineType.BASE_SCHEMA, draft.getBaseSchemaId(), schemaJson);
        }
        if (draft.getCurrentAggregateId() != null) {
            SchemaDraftAggregateRevisionNode previous = currentAggregate(draft);
            String schemaJson = jsonSupport.canonical(project(draft, applyDecisions(
                draft, effectiveCandidates(draft, previous))));
            return baseline(DiffBaselineType.PREVIOUS_AGGREGATE, previous.getId(), schemaJson);
        }
        return emptyBaseline();
    }

    private BaselineSnapshot comparisonBaseline(
        SchemaDraftNode draft, SchemaDraftAggregateRevisionNode aggregate
    ) {
        if (aggregate.getDiffBaselineType() != null) {
            return new BaselineSnapshot(aggregate.getDiffBaselineType(), aggregate.getDiffBaselineId(),
                aggregate.getDiffBaselineSchemaJson(), aggregate.getDiffBaselineContentHash());
        }
        if (draft.getBaseSchemaId() != null) {
            String schemaJson = canonicalSchema(schemaRepository.findById(draft.getBaseSchemaId()).orElseThrow().getContent());
            return baseline(DiffBaselineType.BASE_SCHEMA, draft.getBaseSchemaId(), schemaJson);
        }
        SchemaDraftAggregateRevisionNode previous = aggregateRepository
            .findLegacyPreviousPromoted(draft.getId(), aggregate.getId()).orElse(null);
        if (previous == null) {
            previous = aggregateRepository.findByDraftIdOrderByRevisionDesc(draft.getId()).stream()
                .filter(value -> value.getRevision() < aggregate.getRevision()).findFirst().orElse(null);
        }
        if (previous != null) {
            String schemaJson = canonicalSchema(previous.getSchemaJson());
            return baseline(DiffBaselineType.PREVIOUS_AGGREGATE, previous.getId(), schemaJson);
        }
        return emptyBaseline();
    }

    private BaselineSnapshot emptyBaseline() {
        return baseline(DiffBaselineType.EMPTY, null, jsonSupport.canonical(objectMapper.createObjectNode()));
    }

    private BaselineSnapshot baseline(DiffBaselineType type, String id, String schemaJson) {
        return new BaselineSnapshot(type, id, schemaJson, jsonSupport.fingerprint(schemaJson));
    }

    private String canonicalSchema(String schemaJson) {
        return jsonSupport.canonical(jsonSupport.parse(schemaJson));
    }

    private Map<String, JsonNode> flatten(JsonNode schema) {
        Map<String, JsonNode> values = new TreeMap<>();
        if (schema == null) return values;
        for (JsonNode node : iterable(schema.path("nodes"))) {
            String label = node.path("label").asText();
            values.put("node:" + label, node.path("label"));
            values.put("node-key:" + label, node.path("key"));
            for (JsonNode property : iterable(node.path("properties"))) {
                values.put("node-property:" + label + ":" + property.path("name").asText(), property);
            }
        }
        for (JsonNode relationship : iterable(schema.path("relationships"))) {
            String coordinate = relationshipCoordinate(relationship.path("type").asText(),
                relationship.path("from").asText(), relationship.path("to").asText());
            values.put("relationship:" + coordinate, relationship.path("type"));
            for (JsonNode property : iterable(relationship.path("properties"))) {
                values.put("relationship-property:" + coordinate + ":" + property.path("name").asText(), property);
            }
        }
        return values;
    }

    private SchemaDraftCompatibility classify(String coordinate, JsonNode before, JsonNode after) {
        if (before == null) {
            if (coordinate.contains("property:") && after != null && !after.path("required").asBoolean(false)) {
                return SchemaDraftCompatibility.ADDITIVE;
            }
            return coordinate.startsWith("node:") || coordinate.startsWith("relationship:")
                ? SchemaDraftCompatibility.ADDITIVE : SchemaDraftCompatibility.REVIEW_REQUIRED;
        }
        if (after == null || coordinate.startsWith("node-key:") || coordinate.startsWith("relationship:")) {
            return SchemaDraftCompatibility.BREAKING;
        }
        if (coordinate.contains("property:")) {
            String oldType = before.path("type").asText();
            String newType = after.path("type").asText();
            return wider(oldType, newType) ? SchemaDraftCompatibility.REVIEW_REQUIRED : SchemaDraftCompatibility.BREAKING;
        }
        return SchemaDraftCompatibility.REVIEW_REQUIRED;
    }

    private boolean wider(String before, String after) {
        return before.equals(after) || ("INTEGER".equals(before) && Set.of("LONG", "FLOAT", "DOUBLE").contains(after))
            || ("LONG".equals(before) && Set.of("FLOAT", "DOUBLE").contains(after))
            || ("FLOAT".equals(before) && "DOUBLE".equals(after));
    }

    private void validateResultIdentity(DecisionRequest request, Candidate current) {
        if (request.resultingValue() == null) return;
        Candidate replacement = objectMapper.convertValue(request.resultingValue(), Candidate.class);
        if (!request.candidateIdentity().equals(replacement.identity())) {
            throw new IllegalArgumentException("resultingValue must preserve the candidate identity");
        }
        if (current != null && replacement.kind() != current.kind()) {
            throw new IllegalArgumentException("resultingValue must preserve the candidate kind");
        }
    }

    private SchemaDraftAggregateRevisionNode currentAggregate(SchemaDraftNode draft) {
        if (draft.getCurrentAggregateId() == null) {
            throw new ConflictException("Schema draft has no current analysis result");
        }
        return aggregateRepository.findById(draft.getCurrentAggregateId())
            .orElseThrow(() -> new NotFoundException("Current schema draft aggregate not found"));
    }

    private List<Candidate> readCandidates(String json) {
        try {
            return objectMapper.readerForListOf(Candidate.class).readValue(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Persisted draft candidates are invalid", exception);
        }
    }

    private Map<String, SchemaDraftDecisionNode> latestDecisions(String draftId) {
        Map<String, SchemaDraftDecisionNode> latest = new LinkedHashMap<>();
        decisionRepository.findByDraftIdOrderBySequenceAsc(draftId).forEach(value -> latest.put(value.getCandidateIdentity(), value));
        return latest;
    }

    private SchemaDraftDecisionNode latestDecision(String draftId, String identity) {
        return decisionRepository.findByDraftIdAndCandidateIdentityOrderBySequenceDesc(draftId, identity).stream().findFirst().orElse(null);
    }

    private SchemaDraftReviewState reviewState(SchemaDraftDecisionType type) {
        return switch (type) {
            case ACCEPT -> SchemaDraftReviewState.ACCEPTED;
            case REJECT -> SchemaDraftReviewState.REJECTED;
            case MODIFY, RESOLVE -> SchemaDraftReviewState.MODIFIED;
            case PIN -> SchemaDraftReviewState.PINNED;
        };
    }

    private DecisionResponse toResponse(SchemaDraftDecisionNode decision) {
        return new DecisionResponse(decision.getId(), decision.getSequence(), decision.getDraftRevision(), decision.getType(),
            decision.getReviewState(), decision.getCandidateIdentity(), plainOrNull(decision.getPriorValueJson()),
            plainOrNull(decision.getResultingValueJson()), decision.getRationale(), decision.getCreatedAt());
    }

    private ConflictResponse toResponse(SchemaDraftConflictNode conflict, String currentAggregateId) {
        return new ConflictResponse(conflict.getId(), conflict.getType(), conflict.getCoordinate(),
            toPlain(jsonSupport.parse(conflict.getAlternativesJson())), toPlain(jsonSupport.parse(conflict.getEvidenceJson())),
            conflict.isResolved(), conflict.getSelectedAlternative(), plainOrNull(conflict.getCustomResolutionJson()),
            conflict.getAggregateRevisionId(), conflict.getAggregateRevisionId().equals(currentAggregateId),
            conflict.getCreatedAt(), conflict.getResolvedAt());
    }

    private JsonNode jsonOrNull(String value) {
        return value == null ? null : jsonSupport.parse(value);
    }

    private Object plainOrNull(String value) {
        return value == null ? null : toPlain(jsonSupport.parse(value));
    }

    private Object toPlain(JsonNode value) {
        return value == null ? null : objectMapper.convertValue(value, Object.class);
    }

    private Candidate copy(Candidate value, Set<EvidenceOrigin> origins) {
        return new Candidate(value.kind(), value.identity(), value.label(), value.property(), value.propertyType(), value.keys(),
            value.relationshipType(), value.fromLabel(), value.toLabel(), value.originalLabel(), value.originalProperty(),
            value.originalRelationshipType(), value.confidence(), origins, value.evidence(), value.supportCount(), value.reviewState());
    }

    private String relationshipCoordinate(String type, String from, String to) {
        return type + ":" + from + ":" + to;
    }

    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
    private Iterable<JsonNode> iterable(JsonNode value) { return value != null && value.isArray() ? value : List.of(); }
    private record BaselineSnapshot(DiffBaselineType type, String id, String schemaJson, String contentHash) { }
}
