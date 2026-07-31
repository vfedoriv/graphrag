package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.GraphFact;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.CitationKind;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.ParentContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QueryEvidenceAssemblyService {

    public AdvancedQueryEvidenceAssembly assembleAdvanced(List<EvidenceCandidate> rankedCandidates) {
        List<EvidenceCandidate> candidates = rankedCandidates == null ? List.of() : List.copyOf(rankedCandidates);
        Map<String, GraphFact> graphFacts = new LinkedHashMap<>();
        Map<String, ParentContext> contexts = new LinkedHashMap<>();
        for (EvidenceCandidate candidate : candidates) {
            if (candidate.citationKind() == CitationKind.TEXT_CHILD
                && candidate.source().chunkIndex() < 0) {
                throw new IllegalArgumentException("Text evidence must retain precise child identity");
            }
            if (candidate.citationKind() == CitationKind.GRAPH_PARENT && candidate.parentContext() != null) {
                throw new IllegalArgumentException("Graph-parent evidence cannot be rewritten as text context");
            }
            for (GraphFact fact : candidate.graphFacts()) {
                graphFacts.merge(fact.canonicalFactId(), fact, AdvancedSearchGraphFacts::merge);
            }
            ParentContext context = candidate.parentContext();
            if (context != null) {
                contexts.putIfAbsent(context.kind() + ":" + context.contextChunkId(), context);
            }
        }
        return new AdvancedQueryEvidenceAssembly(
            candidates,
            List.copyOf(contexts.values()),
            List.copyOf(graphFacts.values())
        );
    }

    public record AdvancedQueryEvidenceAssembly(
        List<EvidenceCandidate> candidates,
        List<ParentContext> contexts,
        List<GraphFact> graphFacts
    ) { }
}
