package io.github.vfedoriv.graphrag.search.retrieval.application;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphFacts;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.GraphFact;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.search.retrieval.ports.AdvancedSearchEvidenceExpansionRepository;
import io.github.vfedoriv.graphrag.search.retrieval.ports.AdvancedSearchEvidenceExpansionRepository.ExpansionRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdvancedSearchGraphExpansionService {

    private final AdvancedSearchEvidenceExpansionRepository repository;

    public AdvancedSearchGraphExpansionService(AdvancedSearchEvidenceExpansionRepository repository) {
        this.repository = repository;
    }

    public ExpansionResult expand(
        String knowledgeBaseId,
        List<EvidenceCandidate> rankedCandidates,
        int seedLimit,
        int factLimit
    ) {
        int boundedSeeds = Math.min(
            Math.max(0, seedLimit),
            io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.MAX_EXPANSION_SEEDS
        );
        int boundedFacts = Math.max(0, factLimit);
        List<EvidenceCandidate> candidates = rankedCandidates == null ? List.of() : List.copyOf(rankedCandidates);
        List<String> seedIds = candidates.stream().limit(boundedSeeds).map(EvidenceCandidate::chunkId).toList();
        List<ExpansionRow> rows = repository.expand(knowledgeBaseId, seedIds, boundedFacts);
        Map<String, Map<String, GraphFact>> factsBySeed = new LinkedHashMap<>();
        for (ExpansionRow row : rows) {
            factsBySeed.computeIfAbsent(row.seedChunkId(), ignored -> new LinkedHashMap<>())
                .merge(row.fact().canonicalFactId(), row.fact(), AdvancedSearchGraphFacts::merge);
        }
        List<EvidenceCandidate> expanded = new ArrayList<>();
        int attached = 0;
        for (EvidenceCandidate candidate : candidates) {
            Map<String, GraphFact> facts = new LinkedHashMap<>();
            candidate.graphFacts().forEach(
                fact -> facts.merge(fact.canonicalFactId(), fact, AdvancedSearchGraphFacts::merge)
            );
            Map<String, GraphFact> additions = factsBySeed.getOrDefault(candidate.chunkId(), Map.of());
            additions.forEach((key, fact) -> facts.merge(key, fact, AdvancedSearchGraphFacts::merge));
            attached += Math.max(0, facts.size() - candidate.graphFacts().size());
            expanded.add(candidate.withGraphFacts(List.copyOf(facts.values())));
        }
        return new ExpansionResult(List.copyOf(expanded), new ExpansionDiagnostics(seedIds.size(), rows.size(), attached));
    }

    public record ExpansionDiagnostics(int seedCount, int sourceRowCount, int attachedFactCount) { }

    public record ExpansionResult(List<EvidenceCandidate> candidates, ExpansionDiagnostics diagnostics) { }
}
