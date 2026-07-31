package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.GraphFact;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.CitationKind;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.ParentContext;
import io.github.vfedoriv.graphrag.dto.HybridSearchCitationKind;
import io.github.vfedoriv.graphrag.dto.HybridSearchExpandedContext;
import io.github.vfedoriv.graphrag.dto.HybridSearchGraphEvidence;
import io.github.vfedoriv.graphrag.dto.HybridSearchHit;
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

    public QueryEvidenceAssembly assemble(
        List<HybridSearchHit> rankedHits,
        List<HybridSearchExpandedContext> expandedContexts
    ) {
        List<HybridSearchHit> hits = rankedHits == null ? List.of() : List.copyOf(rankedHits);
        List<HybridSearchExpandedContext> contexts =
            expandedContexts == null ? List.of() : List.copyOf(expandedContexts);
        Map<String, HybridSearchGraphEvidence> graphEvidence = new LinkedHashMap<>();
        for (HybridSearchHit hit : hits) {
            if (hit.retrievalEvidence().citationKind() != HybridSearchCitationKind.TEXT_CHILD) {
                throw new IllegalArgumentException("Text retrieval evidence must retain child citations");
            }
            for (HybridSearchGraphEvidence evidence : hit.graph().evidence()) {
                if (evidence.citationKind() != HybridSearchCitationKind.GRAPH_PARENT) {
                    throw new IllegalArgumentException("Graph evidence must retain authoritative parent citations");
                }
                graphEvidence.putIfAbsent(evidence.evidenceId(), evidence);
            }
        }
        for (HybridSearchExpandedContext context : contexts) {
            if (context.citationKind() != HybridSearchCitationKind.CONTEXT_ONLY) {
                throw new IllegalArgumentException("Expanded parent text must remain synthesis-only context");
            }
        }
        return new QueryEvidenceAssembly(hits, contexts, List.copyOf(graphEvidence.values()));
    }

    public record QueryEvidenceAssembly(
        List<HybridSearchHit> hits,
        List<HybridSearchExpandedContext> contexts,
        List<HybridSearchGraphEvidence> graphEvidence
    ) {
    }

    public record AdvancedQueryEvidenceAssembly(
        List<EvidenceCandidate> candidates,
        List<ParentContext> contexts,
        List<GraphFact> graphFacts
    ) { }
}
