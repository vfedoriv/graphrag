package io.github.vfedoriv.graphrag.search.ranking.domain;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphFacts;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.GraphFact;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.ParentCitation;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Row;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.Channel;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.ChannelContribution;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.CitationKind;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.EvidenceSource;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.SourceBounds;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Branch;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Candidate;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Result;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.SourceIdentity;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Status;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AdvancedSearchFusionService {

    public FusionResult fuse(
        List<Result> textResults,
        io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Result graphResult,
        FusionOptions options
    ) {
        FusionOptions effective = options == null ? FusionOptions.defaults() : options;
        Map<String, MutableCandidate> candidates = new LinkedHashMap<>();
        Map<Channel, Integer> acceptedByChannel = new EnumMap<>(Channel.class);
        Map<Channel, Integer> truncatedByChannel = new EnumMap<>(Channel.class);

        List<Candidate> textCandidates = normalizeTextResults(textResults);
        for (Candidate candidate : textCandidates) {
            Channel channel = channel(candidate.branch());
            if (candidate.rank() > effective.perBranchCandidateLimit()) {
                increment(truncatedByChannel, channel);
                continue;
            }
            SourceIdentity source = candidate.source();
            if (!validSource(source)) {
                increment(truncatedByChannel, channel);
                continue;
            }
            MutableCandidate normalized = candidates.computeIfAbsent(
                source.chunkId(),
                ignored -> MutableCandidate.text(source, candidate.text())
            );
            normalized.addContribution(new ChannelContribution(
                channel,
                candidate.subqueryId(),
                candidate.rank(),
                candidate.rawScore(),
                bounds(source)
            ));
            normalized.preferText(candidate.text());
            increment(acceptedByChannel, channel);
        }

        int graphDerived = addGraphCandidates(candidates, graphResult, effective, acceptedByChannel, truncatedByChannel);
        Map<Channel, Integer> executedCounts = executedCounts(textCandidates, effective.executedSubqueries());
        List<EvidenceCandidate> ranked = candidates.values().stream()
            .map(candidate -> candidate.build(score(candidate.contributions, executedCounts, effective.weights())))
            .sorted(Comparator.comparingDouble(EvidenceCandidate::fusedScore).reversed()
                .thenComparing(EvidenceCandidate::chunkId))
            .limit(effective.fusionPoolLimit())
            .toList();
        List<EvidenceCandidate> withRanks = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            withRanks.add(ranked.get(index).withFusion(ranked.get(index).fusedScore(), index + 1));
        }
        int poolTruncated = Math.max(0, candidates.size() - withRanks.size());
        return new FusionResult(
            List.copyOf(withRanks),
            new FusionDiagnostics(
                Map.copyOf(acceptedByChannel),
                Map.copyOf(truncatedByChannel),
                Map.copyOf(executedCounts),
                candidates.size(),
                poolTruncated,
                graphDerived
            )
        );
    }

    private List<Candidate> normalizeTextResults(List<Result> results) {
        if (results == null) {
            return List.of();
        }
        return results.stream()
            .filter(result -> result != null && result.diagnostics().status() == Status.COMPLETED)
            .flatMap(result -> result.candidates().stream())
            .sorted(Comparator.comparing((Candidate candidate) -> candidate.branch().name())
                .thenComparing(candidate -> candidate.subqueryId() == null ? "" : candidate.subqueryId())
                .thenComparingInt(Candidate::rank)
                .thenComparing(candidate -> candidate.source().chunkId()))
            .toList();
    }

    private int addGraphCandidates(
        Map<String, MutableCandidate> candidates,
        io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Result result,
        FusionOptions options,
        Map<Channel, Integer> accepted,
        Map<Channel, Integer> truncated
    ) {
        if (result == null
            || result.diagnostics().status()
                != io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.Status.COMPLETED) {
            return 0;
        }
        int derived = 0;
        int graphRank = 0;
        Set<String> graphKeys = new LinkedHashSet<>();
        for (Row row : result.rows()) {
            graphRank++;
            if (graphRank > options.perBranchCandidateLimit()) {
                increment(truncated, Channel.GRAPH);
                continue;
            }
            for (GraphFact fact : row.facts()) {
                for (ParentCitation citation : fact.citations()) {
                    if (!validCitation(citation)) {
                        increment(truncated, Channel.GRAPH);
                        continue;
                    }
                    boolean newGraphCandidate = !candidates.containsKey(citation.chunkId());
                    if (newGraphCandidate) {
                        if (graphKeys.contains(citation.chunkId())) {
                            increment(truncated, Channel.GRAPH);
                            continue;
                        }
                        graphKeys.add(citation.chunkId());
                        if (derived >= options.graphDerivedCandidateLimit()) {
                            increment(truncated, Channel.GRAPH);
                            continue;
                        }
                        derived++;
                    }
                    MutableCandidate candidate = candidates.computeIfAbsent(
                        citation.chunkId(),
                        ignored -> MutableCandidate.graph(citation)
                    );
                    candidate.addContribution(new ChannelContribution(
                        Channel.GRAPH,
                        null,
                        graphRank,
                        0.0,
                        bounds(citation)
                    ));
                    candidate.addFact(fact);
                    increment(accepted, Channel.GRAPH);
                }
            }
        }
        return derived;
    }

    private Map<Channel, Integer> executedCounts(
        List<Candidate> candidates,
        Map<Channel, Integer> configured
    ) {
        Map<Channel, Set<String>> subqueries = new EnumMap<>(Channel.class);
        for (Candidate candidate : candidates) {
            Channel channel = channel(candidate.branch());
            String subquery = candidate.subqueryId() == null ? "branch" : candidate.subqueryId();
            subqueries.computeIfAbsent(channel, ignored -> new LinkedHashSet<>()).add(subquery);
        }
        Map<Channel, Integer> counts = new EnumMap<>(Channel.class);
        for (Channel channel : Channel.values()) {
            int inferred = Math.max(1, subqueries.getOrDefault(channel, Set.of()).size());
            int value = configured.getOrDefault(channel, inferred);
            counts.put(channel, Math.max(1, value));
        }
        return counts;
    }

    private double score(
        List<ChannelContribution> contributions,
        Map<Channel, Integer> executedCounts,
        Map<Channel, Double> weights
    ) {
        double score = 0.0;
        Set<String> seen = new LinkedHashSet<>();
        for (ChannelContribution contribution : contributions) {
            String key = contribution.channel() + ":" + contribution.subqueryId();
            if (!seen.add(key)) {
                continue;
            }
            double weight = weights.getOrDefault(contribution.channel(), 1.0);
            int queryCount = executedCounts.getOrDefault(contribution.channel(), 1);
            score += weight / queryCount
                / (io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.RRF_K
                    + contribution.rawRank());
        }
        return score;
    }

    private Channel channel(Branch branch) {
        return Channel.valueOf(branch.name());
    }

    private boolean validSource(SourceIdentity source) {
        return source != null && hasText(source.chunkId()) && hasText(source.documentId());
    }

    private boolean validCitation(ParentCitation citation) {
        return citation != null && hasText(citation.chunkId()) && hasText(citation.documentId());
    }

    private SourceBounds bounds(SourceIdentity source) {
        return new SourceBounds(source.sourceStart(), source.sourceEnd(), source.pageStart(), source.pageEnd());
    }

    private SourceBounds bounds(ParentCitation citation) {
        return new SourceBounds(
            citation.sourceStart(), citation.sourceEnd(), citation.pageStart(), citation.pageEnd()
        );
    }

    private void increment(Map<Channel, Integer> counts, Channel channel) {
        counts.merge(channel, 1, Integer::sum);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record FusionOptions(
        int fusionPoolLimit,
        int graphDerivedCandidateLimit,
        int perBranchCandidateLimit,
        Map<Channel, Integer> executedSubqueries,
        Map<Channel, Double> weights
    ) {
        public FusionOptions {
            if (fusionPoolLimit < 1
                || fusionPoolLimit > io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.MAX_FUSION_POOL) {
                throw new IllegalArgumentException("fusionPoolLimit is outside the supported bound");
            }
            if (graphDerivedCandidateLimit < 0
                || graphDerivedCandidateLimit
                    > io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.MAX_GRAPH_DERIVED_CANDIDATES) {
                throw new IllegalArgumentException("graphDerivedCandidateLimit is outside the supported bound");
            }
            if (perBranchCandidateLimit < 1
                || perBranchCandidateLimit
                    > io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.MAX_CANDIDATES) {
                throw new IllegalArgumentException("perBranchCandidateLimit is outside the supported bound");
            }
            executedSubqueries = executedSubqueries == null ? Map.of() : Map.copyOf(executedSubqueries);
            weights = weights == null ? Map.of() : Map.copyOf(weights);
            if (weights.values().stream().anyMatch(weight -> weight == null || weight < 0.0)) {
                throw new IllegalArgumentException("channel weights must not be negative");
            }
        }

        public static FusionOptions defaults() {
            return new FusionOptions(60, 20, 60, Map.of(), Map.of());
        }
    }

    public record FusionDiagnostics(
        Map<Channel, Integer> acceptedByChannel,
        Map<Channel, Integer> truncatedByChannel,
        Map<Channel, Integer> executedSubqueries,
        int deduplicatedCandidateCount,
        int poolTruncatedCount,
        int graphDerivedCandidateCount
    ) { }

    public record FusionResult(List<EvidenceCandidate> candidates, FusionDiagnostics diagnostics) { }

    private static final class MutableCandidate {

        private final EvidenceSource source;
        private final CitationKind citationKind;
        private String text;
        private final List<ChannelContribution> contributions = new ArrayList<>();
        private final Map<String, GraphFact> facts = new LinkedHashMap<>();

        private MutableCandidate(EvidenceSource source, CitationKind citationKind, String text) {
            this.source = source;
            this.citationKind = citationKind;
            this.text = text;
        }

        private static MutableCandidate text(SourceIdentity source, String text) {
            return new MutableCandidate(
                new EvidenceSource(
                    source.chunkId(),
                    source.documentId(),
                    source.chunkIndex(),
                    new SourceBounds(source.sourceStart(), source.sourceEnd(), source.pageStart(), source.pageEnd()),
                    source.processingRunId(),
                    source.effectiveChunkerRevision(),
                    source.structuralPath()
                ),
                CitationKind.TEXT_CHILD,
                text
            );
        }

        private static MutableCandidate graph(ParentCitation citation) {
            return new MutableCandidate(
                new EvidenceSource(
                    citation.chunkId(),
                    citation.documentId(),
                    -1,
                    new SourceBounds(
                        citation.sourceStart(), citation.sourceEnd(), citation.pageStart(), citation.pageEnd()
                    ),
                    citation.processingRunId(),
                    citation.effectiveChunkerRevision(),
                    citation.structuralPath()
                ),
                CitationKind.GRAPH_PARENT,
                null
            );
        }

        private void addContribution(ChannelContribution contribution) {
            for (int index = 0; index < contributions.size(); index++) {
                ChannelContribution existing = contributions.get(index);
                if (existing.channel() == contribution.channel()
                    && java.util.Objects.equals(existing.subqueryId(), contribution.subqueryId())) {
                    if (contribution.rawRank() < existing.rawRank()) {
                        contributions.set(index, contribution);
                    }
                    return;
                }
            }
            contributions.add(contribution);
        }

        private void addFact(GraphFact fact) {
            facts.merge(fact.canonicalFactId(), fact, AdvancedSearchGraphFacts::merge);
        }

        private void preferText(String candidateText) {
            if (text == null && candidateText != null) {
                text = candidateText;
            }
        }

        private EvidenceCandidate build(double score) {
            List<ChannelContribution> ordered = contributions.stream()
                .sorted(Comparator.comparing((ChannelContribution value) -> value.channel().name())
                    .thenComparing(value -> value.subqueryId() == null ? "" : value.subqueryId())
                    .thenComparingInt(ChannelContribution::rawRank))
                .toList();
            return new EvidenceCandidate(
                source,
                citationKind,
                text,
                ordered,
                List.copyOf(facts.values()),
                null,
                score,
                0,
                null
            );
        }
    }
}
