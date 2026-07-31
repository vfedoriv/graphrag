package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchAnswerContracts.CitationType;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchAnswerContracts.Evidence;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchAnswerContracts.GraphFact;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchAnswerContracts.SourceRange;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.ParentCitation;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.CitationKind;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.ParentContext;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.SourceBounds;
import io.github.vfedoriv.graphrag.service.QueryEvidenceAssemblyService.AdvancedQueryEvidenceAssembly;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AdvancedSearchCitationCatalog {

    public Catalog build(AdvancedQueryEvidenceAssembly assembly, boolean includeEvidenceText) {
        List<Evidence> evidence = new ArrayList<>();
        Map<String, String> citationByChunk = new LinkedHashMap<>();
        int citationSequence = 1;
        for (EvidenceCandidate candidate : assembly.candidates()) {
            String key = candidate.citationKind().name() + ":" + candidate.chunkId();
            if (citationByChunk.containsKey(key)) {
                continue;
            }
            String citationId = "E" + citationSequence++;
            citationByChunk.put(key, citationId);
            evidence.add(new Evidence(
                citationId,
                candidate.citationKind() == CitationKind.TEXT_CHILD
                    ? CitationType.TEXT_CHILD : CitationType.GRAPH_PARENT,
                candidate.chunkId(),
                candidate.documentId(),
                range(candidate.source().bounds()),
                candidate.source().processingRunId(),
                candidate.source().effectiveChunkerRevision(),
                candidate.source().structuralPath(),
                includeEvidenceText ? candidate.text() : null,
                candidate.fusedRank(),
                candidate.rerankScore() == null ? candidate.fusedScore() : candidate.rerankScore()
            ));
        }

        List<Evidence> contexts = new ArrayList<>();
        int contextSequence = 1;
        for (ParentContext context : assembly.contexts()) {
            contexts.add(new Evidence(
                "CTX" + contextSequence++, CitationType.CONTEXT_ONLY,
                context.contextChunkId(), context.documentId(), range(context.bounds()), null,
                context.effectiveChunkerRevision(), null, includeEvidenceText ? context.text() : null, 0, null
            ));
        }

        List<GraphFact> facts = new ArrayList<>();
        Map<String, Set<String>> factCitations = new LinkedHashMap<>();
        for (io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.GraphFact fact
            : assembly.graphFacts()) {
            Set<String> citationIds = new LinkedHashSet<>();
            for (ParentCitation citation : fact.citations()) {
                String citationId = citationByChunk.get(CitationKind.GRAPH_PARENT.name() + ":" + citation.chunkId());
                if (citationId != null) {
                    citationIds.add(citationId);
                }
            }
            if (citationIds.isEmpty()) {
                for (Evidence entry : evidence) {
                    if (entry.type() == CitationType.GRAPH_PARENT) {
                        citationIds.add(entry.citationId());
                    }
                }
            }
            factCitations.put(fact.canonicalFactId(), citationIds);
            facts.add(new GraphFact(fact.canonicalFactId(), fact.evidenceIds(), List.copyOf(citationIds)));
        }
        return new Catalog(List.copyOf(evidence), List.copyOf(contexts), List.copyOf(facts), factCitations);
    }

    private SourceRange range(SourceBounds bounds) {
        return new SourceRange(bounds.sourceStart(), bounds.sourceEnd(), bounds.pageStart(), bounds.pageEnd());
    }

    public record Catalog(
        List<Evidence> evidence,
        List<Evidence> contexts,
        List<GraphFact> graphFacts,
        Map<String, Set<String>> factCitations
    ) {
        public Catalog {
            evidence = List.copyOf(evidence);
            contexts = List.copyOf(contexts);
            graphFacts = List.copyOf(graphFacts);
            Map<String, Set<String>> copied = new LinkedHashMap<>();
            factCitations.forEach((key, value) -> copied.put(key, Set.copyOf(value)));
            factCitations = Map.copyOf(copied);
        }

        public Set<String> citableIds() {
            Set<String> ids = new LinkedHashSet<>();
            evidence.forEach(value -> ids.add(value.citationId()));
            return Set.copyOf(ids);
        }

        public Set<String> graphEvidenceIds() {
            Set<String> ids = new LinkedHashSet<>();
            graphFacts.forEach(value -> ids.addAll(value.evidenceIds()));
            return Set.copyOf(ids);
        }

        public Catalog withoutText() {
            return new Catalog(
                evidence.stream().map(AdvancedSearchCitationCatalog::withoutText).toList(),
                contexts.stream().map(AdvancedSearchCitationCatalog::withoutText).toList(),
                graphFacts,
                factCitations
            );
        }
    }

    private static Evidence withoutText(Evidence value) {
        return new Evidence(
            value.citationId(), value.type(), value.chunkId(), value.documentId(), value.range(),
            value.processingRunId(), value.effectiveChunkerRevision(), value.structuralPath(), null,
            value.rank(), value.score()
        );
    }
}
