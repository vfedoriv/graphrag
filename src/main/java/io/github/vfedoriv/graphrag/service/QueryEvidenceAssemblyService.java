package io.github.vfedoriv.graphrag.service;

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
}
