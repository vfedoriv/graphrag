package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.GraphFact;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.ParentCitation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AdvancedSearchGraphFacts {

    private AdvancedSearchGraphFacts() { }

    static GraphFact merge(GraphFact first, GraphFact second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        if (!first.canonicalFactId().equals(second.canonicalFactId())) {
            throw new IllegalArgumentException("Only identical graph facts can be merged");
        }
        Set<String> evidenceIds = new LinkedHashSet<>(first.evidenceIds());
        evidenceIds.addAll(second.evidenceIds());
        Map<String, ParentCitation> citations = new LinkedHashMap<>();
        first.citations().forEach(citation -> citations.putIfAbsent(citationKey(citation), citation));
        second.citations().forEach(citation -> citations.putIfAbsent(citationKey(citation), citation));
        Map<String, Object> properties = new LinkedHashMap<>(first.properties());
        second.properties().forEach(properties::putIfAbsent);
        return new GraphFact(
            first.canonicalFactId(),
            first.schema(),
            properties,
            List.copyOf(evidenceIds),
            List.copyOf(citations.values())
        );
    }

    private static String citationKey(ParentCitation citation) {
        return citation.chunkId() + ":" + citation.processingRunId();
    }
}
