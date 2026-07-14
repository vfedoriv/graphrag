package io.github.vfedoriv.graphrag.discovery;

import io.github.vfedoriv.graphrag.discovery.CandidateExtractionResult.AliasSuggestion;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.Candidate;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.CandidateKind;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.Evidence;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.EvidenceOrigin;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.ReviewState;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
public class DiscoverySourceAnalyzer {

    private final CandidateExtractionPromptFactory promptFactory;
    private final CandidateExtractionModelAdapter modelAdapter;

    public DiscoverySourceAnalyzer(
        CandidateExtractionPromptFactory promptFactory, CandidateExtractionModelAdapter modelAdapter
    ) {
        this.promptFactory = promptFactory;
        this.modelAdapter = modelAdapter;
    }

    public SourceAnalysis analyze(PreparedDiscoverySource source, SchemaDiscoveryRequest request) {
        BeanOutputConverter<CandidateExtractionResult> converter = new BeanOutputConverter<>(CandidateExtractionResult.class);
        List<Candidate> candidates = new ArrayList<>();
        List<AliasSuggestion> aliases = new ArrayList<>();
        for (PreparedDiscoverySource.AnalysisChunk chunk : source.chunks()) {
            String nativePrompt = promptFactory.prompt(source, chunk, request, null);
            String portablePrompt = promptFactory.prompt(source, chunk, request, converter.getFormat());
            CandidateExtractionResult result = modelAdapter.extract(nativePrompt, portablePrompt);
            candidates.addAll(toCandidates(source, chunk, result));
            aliases.addAll(result.aliasSuggestions());
        }
        return new SourceAnalysis(source, List.copyOf(candidates), List.copyOf(aliases));
    }

    private List<Candidate> toCandidates(
        PreparedDiscoverySource source, PreparedDiscoverySource.AnalysisChunk chunk, CandidateExtractionResult result
    ) {
        List<Candidate> candidates = new ArrayList<>();
        result.nodes().forEach(value -> candidates.add(candidate(CandidateKind.NODE, value.label(), null, null,
            null, null, null, List.of(), value.confidence(), value.origin(), source, chunk)));
        result.nodeProperties().forEach(value -> candidates.add(candidate(CandidateKind.NODE_PROPERTY, value.nodeLabel(),
            value.name(), value.type(), null, null, null, List.of(), value.confidence(), value.origin(), source, chunk)));
        result.nodeKeys().forEach(value -> candidates.add(candidate(CandidateKind.NODE_KEY, value.nodeLabel(), null,
            null, null, null, null, value.properties(), value.confidence(), value.origin(), source, chunk)));
        result.relationships().forEach(value -> candidates.add(candidate(CandidateKind.RELATIONSHIP, null, null, null,
            value.type(), value.fromLabel(), value.toLabel(), List.of(), value.confidence(), value.origin(), source, chunk)));
        result.relationshipProperties().forEach(value -> candidates.add(candidate(CandidateKind.RELATIONSHIP_PROPERTY,
            null, value.name(), value.type(), value.relationshipType(), value.fromLabel(), value.toLabel(), List.of(),
            value.confidence(), value.origin(), source, chunk)));
        candidates.forEach(this::validate);
        return candidates;
    }

    private Candidate candidate(
        CandidateKind kind, String rawLabel, String rawProperty, String propertyType, String rawRelationship,
        String rawFrom, String rawTo, List<String> rawKeys, Double confidence, String rawOrigin,
        PreparedDiscoverySource source, PreparedDiscoverySource.AnalysisChunk chunk
    ) {
        String label = DiscoveryContracts.normalizeLabel(rawLabel);
        String property = DiscoveryContracts.normalizeProperty(rawProperty);
        String relationship = DiscoveryContracts.normalizeRelationship(rawRelationship);
        String from = DiscoveryContracts.normalizeLabel(rawFrom);
        String to = DiscoveryContracts.normalizeLabel(rawTo);
        List<String> keys = rawKeys.stream().map(DiscoveryContracts::normalizeProperty).sorted().toList();
        EvidenceOrigin origin = "INFERRED".equalsIgnoreCase(rawOrigin) ? EvidenceOrigin.INFERRED : EvidenceOrigin.OBSERVED;
        Evidence evidence = new Evidence(source.sourceId(), source.fingerprint(), chunk.id(), source.documentId(), Set.of(origin));
        String identity = DiscoveryContracts.identity(kind, label, property, relationship, from, to);
        return new Candidate(kind, identity, label, property, normalizeType(propertyType), keys, relationship, from, to,
            rawLabel, rawProperty, rawRelationship, confidence, Set.of(origin), List.of(evidence), 1, ReviewState.RECOMMENDED);
    }

    private void validate(Candidate candidate) {
        if (candidate.identity().contains("null") || candidate.identity().endsWith(":")) {
            throw new IllegalArgumentException("Model candidate is missing required coordinates for " + candidate.kind());
        }
        if (candidate.confidence() != null && (candidate.confidence() < 0 || candidate.confidence() > 1)) {
            throw new IllegalArgumentException("Model candidate confidence must be between 0 and 1");
        }
        if (candidate.kind() == CandidateKind.NODE_KEY && candidate.keys().isEmpty()) {
            throw new IllegalArgumentException("Model node-key candidate must include at least one property");
        }
    }

    private String normalizeType(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public record SourceAnalysis(
        PreparedDiscoverySource source, List<Candidate> candidates, List<AliasSuggestion> aliasSuggestions
    ) {
    }
}
