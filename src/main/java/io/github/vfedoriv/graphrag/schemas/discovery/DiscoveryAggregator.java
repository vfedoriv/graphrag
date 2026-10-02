package io.github.vfedoriv.graphrag.schemas.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.schemas.discovery.CandidateExtractionResult.AliasSuggestion;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.Candidate;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.CandidateKind;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.Conflict;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.ConflictCategory;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.Evidence;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.EvidenceOrigin;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.ReviewState;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest.ConceptRule;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest.PropertyRule;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest.RelationshipRule;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryResponse.Warning;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryAggregator {

    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
        .comparing((Candidate value) -> value.kind().name())
        .thenComparing(Candidate::identity)
        .thenComparing(value -> safe(value.propertyType()))
        .thenComparing(value -> String.join(",", value.keys()));
    private final ObjectMapper objectMapper;

    public DiscoveryAggregator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AggregateResult aggregate(
        List<DiscoverySourceAnalyzer.SourceAnalysis> analyses, SchemaDiscoveryRequest request
    ) {
        List<Candidate> raw = analyses.stream().flatMap(result -> result.candidates().stream()).collect(Collectors.toCollection(ArrayList::new));
        addGuidedCandidates(raw, request);
        List<Candidate> candidates = mergeVariants(raw);
        List<Conflict> conflicts = classifyConflicts(candidates, analyses);
        Set<String> excluded = request.guidance().excludedConcepts().stream()
            .map(DiscoveryContracts::normalizeLabel).collect(Collectors.toSet());
        List<Warning> warnings = new ArrayList<>();
        List<Candidate> classified = candidates.stream().map(candidate -> classify(candidate, conflicts, excluded, warnings)).toList();
        addPreferredWarnings(classified, request, warnings);
        JsonNode schema = project(classified, conflicts, warnings);
        List<Conflict> orderedConflicts = conflicts.stream()
            .sorted(Comparator.comparing((Conflict value) -> value.category().name()).thenComparing(Conflict::coordinate))
            .toList();
        warnings.sort(Comparator.comparing(Warning::code).thenComparing(value -> safe(value.coordinate())));
        return new AggregateResult(classified.stream().sorted(CANDIDATE_ORDER).toList(), orderedConflicts, List.copyOf(warnings), schema);
    }

    private List<Candidate> mergeVariants(List<Candidate> raw) {
        Map<String, List<Candidate>> variants = new TreeMap<>();
        for (Candidate candidate : raw) {
            variants.computeIfAbsent(variantKey(candidate), ignored -> new ArrayList<>()).add(candidate);
        }
        return variants.values().stream().map(this::merge).sorted(CANDIDATE_ORDER).toList();
    }

    private Candidate merge(List<Candidate> values) {
        Candidate first = values.getFirst();
        Map<String, Evidence> evidence = new TreeMap<>();
        Set<EvidenceOrigin> origins = new LinkedHashSet<>();
        Double confidence = null;
        for (Candidate candidate : values) {
            origins.addAll(candidate.origins());
            if (candidate.confidence() != null) {
                confidence = confidence == null ? candidate.confidence() : Math.max(confidence, candidate.confidence());
            }
            for (Evidence item : candidate.evidence()) {
                evidence.merge(evidenceKey(item), item, this::mergeEvidence);
            }
        }
        long support = evidence.values().stream().map(this::independentSource).distinct().count();
        return new Candidate(first.kind(), first.identity(), first.label(), first.property(), first.propertyType(), first.keys(),
            first.relationshipType(), first.fromLabel(), first.toLabel(), first.originalLabel(), first.originalProperty(),
            first.originalRelationshipType(), confidence, Set.copyOf(origins), List.copyOf(evidence.values()), (int) support,
            support < 2 && origins.contains(EvidenceOrigin.OBSERVED) ? ReviewState.LOW_SUPPORT : ReviewState.RECOMMENDED);
    }

    private Evidence mergeEvidence(Evidence left, Evidence right) {
        Set<EvidenceOrigin> origins = new LinkedHashSet<>(left.origins());
        origins.addAll(right.origins());
        return new Evidence(left.sourceId(), left.sourceFingerprint(), left.chunkId(), left.documentId(), Set.copyOf(origins));
    }

    private List<Conflict> classifyConflicts(
        List<Candidate> candidates, List<DiscoverySourceAnalyzer.SourceAnalysis> analyses
    ) {
        List<Conflict> conflicts = new ArrayList<>();
        addVariantConflicts(candidates, CandidateKind.NODE_PROPERTY, ConflictCategory.PROPERTY_TYPE,
            candidate -> candidate.identity(), candidate -> candidate.propertyType(), conflicts);
        addVariantConflicts(candidates, CandidateKind.RELATIONSHIP_PROPERTY, ConflictCategory.PROPERTY_TYPE,
            candidate -> candidate.identity(), candidate -> candidate.propertyType(), conflicts);
        addVariantConflicts(candidates, CandidateKind.NODE_KEY, ConflictCategory.IDENTITY_KEY,
            Candidate::identity, candidate -> String.join(",", candidate.keys()), conflicts);

        Map<String, List<Candidate>> endpointGroups = candidates.stream()
            .filter(candidate -> candidate.kind() == CandidateKind.RELATIONSHIP)
            .collect(Collectors.groupingBy(candidate -> candidate.fromLabel() + "->" + candidate.toLabel()));
        for (Map.Entry<String, List<Candidate>> entry : endpointGroups.entrySet()) {
            Set<String> names = entry.getValue().stream().map(Candidate::relationshipType).collect(Collectors.toCollection(LinkedHashSet::new));
            if (names.size() > 1) {
                conflicts.add(conflict(ConflictCategory.RELATIONSHIP_NAME, "relationship-endpoints:" + entry.getKey(), names, entry.getValue()));
            }
        }
        Map<String, List<Candidate>> semanticGroups = candidates.stream()
            .filter(candidate -> candidate.kind() == CandidateKind.RELATIONSHIP)
            .collect(Collectors.groupingBy(candidate -> candidate.relationshipType() + ":" + unorderedPair(candidate.fromLabel(), candidate.toLabel())));
        for (Map.Entry<String, List<Candidate>> entry : semanticGroups.entrySet()) {
            Set<String> directions = entry.getValue().stream().map(candidate -> candidate.fromLabel() + "->" + candidate.toLabel())
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (directions.size() > 1) {
                conflicts.add(conflict(ConflictCategory.RELATIONSHIP_DIRECTION, "relationship-direction:" + entry.getKey(), directions, entry.getValue()));
            }
        }
        for (DiscoverySourceAnalyzer.SourceAnalysis analysis : analyses) {
            for (AliasSuggestion alias : analysis.aliasSuggestions()) {
                String coordinate = DiscoveryContracts.normalizeLabel(alias.first()) + "~" + DiscoveryContracts.normalizeLabel(alias.second());
                Evidence evidence = new Evidence(analysis.source().sourceId(), analysis.source().fingerprint(), null,
                    analysis.source().documentId(), Set.of(EvidenceOrigin.INFERRED));
                conflicts.add(new Conflict(ConflictCategory.ALIAS_SUGGESTION, coordinate,
                    List.of(alias.first(), alias.second()), List.of(evidence), true));
            }
        }
        return conflicts;
    }

    private void addVariantConflicts(
        List<Candidate> candidates, CandidateKind kind, ConflictCategory category,
        Function<Candidate, String> coordinate, Function<Candidate, String> alternative, List<Conflict> conflicts
    ) {
        Map<String, List<Candidate>> groups = candidates.stream().filter(candidate -> candidate.kind() == kind)
            .collect(Collectors.groupingBy(coordinate));
        for (Map.Entry<String, List<Candidate>> entry : groups.entrySet()) {
            Set<String> alternatives = entry.getValue().stream().map(alternative).collect(Collectors.toCollection(LinkedHashSet::new));
            if (alternatives.size() > 1) {
                conflicts.add(conflict(category, entry.getKey(), alternatives, entry.getValue()));
            }
        }
    }

    private Conflict conflict(ConflictCategory category, String coordinate, Collection<String> alternatives, List<Candidate> candidates) {
        List<Evidence> evidence = candidates.stream().flatMap(value -> value.evidence().stream())
            .collect(Collectors.toMap(this::evidenceKey, Function.identity(), this::mergeEvidence, TreeMap::new))
            .values().stream().toList();
        return new Conflict(category, coordinate, alternatives.stream().sorted().toList(), evidence, true);
    }

    private Candidate classify(
        Candidate candidate, List<Conflict> conflicts, Set<String> excluded, List<Warning> warnings
    ) {
        boolean suppressed = excluded.contains(candidate.label()) || excluded.contains(candidate.fromLabel()) || excluded.contains(candidate.toLabel());
        ReviewState state = candidate.reviewState();
        if (suppressed) {
            state = ReviewState.SUPPRESSED;
            warnings.add(new Warning("EXCLUDED_CONCEPT_SUPPRESSED", candidate.identity(),
                "Candidate was suppressed by exclusion guidance", candidate.supportCount()));
        } else if (isConflicted(candidate, conflicts)) {
            state = ReviewState.REVIEW_REQUIRED;
        } else if (candidate.origins().contains(EvidenceOrigin.GUIDED) && !candidate.origins().contains(EvidenceOrigin.OBSERVED)) {
            state = ReviewState.REVIEW_REQUIRED;
            warnings.add(new Warning("REQUIRED_CONCEPT_UNSUPPORTED", candidate.identity(),
                "Required guided candidate has no observed source evidence", 0));
        } else if (candidate.supportCount() < 2) {
            warnings.add(new Warning("LOW_SUPPORT", candidate.identity(), "Candidate is supported by fewer than two independent sources",
                candidate.supportCount()));
        }
        return new Candidate(candidate.kind(), candidate.identity(), candidate.label(), candidate.property(), candidate.propertyType(),
            candidate.keys(), candidate.relationshipType(), candidate.fromLabel(), candidate.toLabel(), candidate.originalLabel(),
            candidate.originalProperty(), candidate.originalRelationshipType(), candidate.confidence(), candidate.origins(),
            candidate.evidence(), candidate.supportCount(), state);
    }

    private void addGuidedCandidates(List<Candidate> candidates, SchemaDiscoveryRequest request) {
        for (ConceptRule rule : request.guidance().requiredConcepts()) {
            addGuidedNode(candidates, rule, true);
        }
        for (PropertyRule rule : request.guidance().propertyRules()) {
            String label = DiscoveryContracts.normalizeLabel(rule.owner());
            String property = DiscoveryContracts.normalizeProperty(rule.name());
            candidates.add(guided(CandidateKind.NODE_PROPERTY, label, property, rule.type(), List.of(), null, null, null));
            if (Boolean.TRUE.equals(rule.identity())) {
                candidates.add(guided(CandidateKind.NODE_KEY, label, null, null, List.of(property), null, null, null));
            }
        }
        for (RelationshipRule rule : request.guidance().relationshipRules()) {
            candidates.add(guided(CandidateKind.RELATIONSHIP, null, null, null, List.of(),
                DiscoveryContracts.normalizeRelationship(rule.type()), DiscoveryContracts.normalizeLabel(rule.from()),
                DiscoveryContracts.normalizeLabel(rule.to())));
        }
    }

    private void addGuidedNode(List<Candidate> candidates, ConceptRule rule, boolean required) {
        String label = DiscoveryContracts.normalizeLabel(rule.name());
        candidates.add(guided(CandidateKind.NODE, label, null, null, List.of(), null, null, null));
        if (required && !rule.identityKeys().isEmpty()) {
            List<String> keys = rule.identityKeys().stream().map(DiscoveryContracts::normalizeProperty).sorted().toList();
            candidates.add(guided(CandidateKind.NODE_KEY, label, null, null, keys, null, null, null));
        }
    }

    private Candidate guided(
        CandidateKind kind, String label, String property, String type, List<String> keys,
        String relationship, String from, String to
    ) {
        String identity = DiscoveryContracts.identity(kind, label, property, relationship, from, to);
        return new Candidate(kind, identity, label, property, type, keys, relationship, from, to, label, property,
            relationship, null, Set.of(EvidenceOrigin.GUIDED), List.of(), 0, ReviewState.REVIEW_REQUIRED);
    }

    private void addPreferredWarnings(List<Candidate> candidates, SchemaDiscoveryRequest request, List<Warning> warnings) {
        Set<String> observed = candidates.stream().filter(candidate -> candidate.kind() == CandidateKind.NODE)
            .filter(candidate -> candidate.origins().contains(EvidenceOrigin.OBSERVED)).map(Candidate::label).collect(Collectors.toSet());
        for (ConceptRule preferred : request.guidance().preferredConcepts()) {
            String label = DiscoveryContracts.normalizeLabel(preferred.name());
            if (!observed.contains(label)) {
                warnings.add(new Warning("PREFERRED_CONCEPT_UNSUPPORTED", "node:" + label,
                    "Preferred concept lacked source evidence", 0));
            }
        }
    }

    private JsonNode project(List<Candidate> candidates, List<Conflict> conflicts, List<Warning> warnings) {
        List<Candidate> eligible = candidates.stream().filter(candidate -> candidate.reviewState() != ReviewState.SUPPRESSED).toList();
        Map<String, List<Candidate>> properties = eligible.stream().filter(candidate -> candidate.kind() == CandidateKind.NODE_PROPERTY)
            .filter(candidate -> !isConflicted(candidate, conflicts)).collect(Collectors.groupingBy(Candidate::label));
        Map<String, List<Candidate>> keys = eligible.stream().filter(candidate -> candidate.kind() == CandidateKind.NODE_KEY)
            .filter(candidate -> !isConflicted(candidate, conflicts)).collect(Collectors.groupingBy(Candidate::label));
        List<SchemaDocument.NodeDefinition> nodes = eligible.stream().filter(candidate -> candidate.kind() == CandidateKind.NODE)
            .map(Candidate::label).distinct().sorted().map(label -> {
                List<SchemaDocument.PropertyDefinition> nodeProperties = properties.getOrDefault(label, List.of()).stream()
                    .sorted(Comparator.comparing(Candidate::property))
                    .map(candidate -> new SchemaDocument.PropertyDefinition(candidate.property(), candidate.propertyType(), false)).toList();
                List<String> nodeKeys = keys.getOrDefault(label, List.of()).stream().findFirst().map(Candidate::keys).orElse(List.of());
                return new SchemaDocument.NodeDefinition(label, null, nodeKeys, nodeProperties);
            }).toList();
        Set<String> nodeLabels = nodes.stream().map(SchemaDocument.NodeDefinition::label).collect(Collectors.toSet());
        Map<String, List<Candidate>> relationshipProperties = eligible.stream()
            .filter(candidate -> candidate.kind() == CandidateKind.RELATIONSHIP_PROPERTY)
            .filter(candidate -> !isConflicted(candidate, conflicts))
            .collect(Collectors.groupingBy(candidate -> relationshipCoordinate(candidate.relationshipType(), candidate.fromLabel(), candidate.toLabel())));
        List<SchemaDocument.RelationshipDefinition> relationships = eligible.stream()
            .filter(candidate -> candidate.kind() == CandidateKind.RELATIONSHIP)
            .filter(candidate -> !isConflicted(candidate, conflicts))
            .filter(candidate -> nodeLabels.contains(candidate.fromLabel()) && nodeLabels.contains(candidate.toLabel()))
            .sorted(Comparator.comparing(Candidate::identity))
            .map(candidate -> new SchemaDocument.RelationshipDefinition(candidate.relationshipType(), candidate.fromLabel(), candidate.toLabel(), null,
                relationshipProperties.getOrDefault(relationshipCoordinate(candidate.relationshipType(), candidate.fromLabel(), candidate.toLabel()), List.of())
                    .stream().sorted(Comparator.comparing(Candidate::property))
                    .map(property -> new SchemaDocument.PropertyDefinition(property.property(), property.propertyType(), false)).toList()))
            .toList();
        SchemaDocument schema = new SchemaDocument("discovered-schema", 1, "Review-only multi-source discovery projection",
            nodes, relationships, List.of(), List.of());
        return objectMapper.valueToTree(schema);
    }

    private String variantKey(Candidate candidate) {
        return candidate.identity() + "|" + safe(candidate.propertyType()) + "|" + String.join(",", candidate.keys());
    }

    private boolean isConflicted(Candidate candidate, List<Conflict> conflicts) {
        for (Conflict conflict : conflicts) {
            if (!conflict.reviewRequired()) {
                continue;
            }
            if (conflict.coordinate().equals(candidate.identity())) {
                return true;
            }
            if (candidate.kind() == CandidateKind.RELATIONSHIP) {
                String direction = candidate.fromLabel() + "->" + candidate.toLabel();
                if (conflict.category() == ConflictCategory.RELATIONSHIP_NAME
                    && conflict.coordinate().equals("relationship-endpoints:" + direction)
                    && conflict.alternatives().contains(candidate.relationshipType())) {
                    return true;
                }
                String semantic = "relationship-direction:" + candidate.relationshipType() + ":"
                    + unorderedPair(candidate.fromLabel(), candidate.toLabel());
                if (conflict.category() == ConflictCategory.RELATIONSHIP_DIRECTION
                    && conflict.coordinate().equals(semantic)
                    && conflict.alternatives().contains(direction)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String evidenceKey(Evidence evidence) {
        return safe(evidence.sourceId()) + "|" + safe(evidence.chunkId());
    }

    private String independentSource(Evidence evidence) {
        return evidence.documentId() == null ? evidence.sourceId() : "document:" + evidence.documentId();
    }

    private String unorderedPair(String left, String right) {
        return left.compareTo(right) <= 0 ? left + ":" + right : right + ":" + left;
    }

    private String relationshipCoordinate(String type, String from, String to) {
        return type + ":" + from + ":" + to;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record AggregateResult(
        List<Candidate> candidates, List<Conflict> conflicts, List<Warning> warnings, JsonNode schema
    ) {
    }
}
