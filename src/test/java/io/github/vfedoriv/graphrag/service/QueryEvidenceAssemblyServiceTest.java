package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.search.answering.domain.QueryEvidenceAssemblyService;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.FactKind;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.GraphFact;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.ParentCitation;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchGraphRetrievalContracts.SchemaRepresentation;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.CitationKind;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.EvidenceSource;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.ParentContext;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.SourceBounds;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryEvidenceAssemblyServiceTest {

    @Test
    void assemblesAdvancedEvidenceWithDeduplicatedFactsAndParentContexts() {
        SourceBounds bounds = new SourceBounds(0, 100, 4, 5);
        ParentContext context = new ParentContext(
            "parent-1", "doc-1", "PARENT", "parent text", bounds,
            "revision-1", List.of("child-1", "child-2"), 25
        );
        GraphFact fact = new GraphFact(
            "entity-1",
            new SchemaRepresentation(FactKind.NODE, "Entity", null, null),
            Map.of("name", "Entity 1"),
            List.of("evidence-1"),
            List.of(new ParentCitation(
                "parent-1", "doc-1", 0, 100, 4, 5, "run-1", "revision-1", "Clause 7"
            ))
        );

        QueryEvidenceAssemblyService.AdvancedQueryEvidenceAssembly assembly =
            new QueryEvidenceAssemblyService().assembleAdvanced(List.of(
                candidate("child-1", fact, context),
                candidate("child-2", fact, context)
            ));

        assertThat(assembly.candidates()).extracting(EvidenceCandidate::citationKind)
            .containsOnly(CitationKind.TEXT_CHILD);
        assertThat(assembly.contexts()).containsExactly(context);
        assertThat(assembly.graphFacts()).containsExactly(fact);
        assertThat(assembly.graphFacts().getFirst().citations().getFirst().chunkId()).isEqualTo("parent-1");
    }

    private EvidenceCandidate candidate(String chunkId, GraphFact fact, ParentContext context) {
        return new EvidenceCandidate(
            new EvidenceSource(
                chunkId, "doc-1", 0, new SourceBounds(10, 20, 4, 4),
                "run-1", "revision-1", "Clause 7"
            ),
            CitationKind.TEXT_CHILD,
            "child text",
            List.of(),
            List.of(fact),
            context,
            0.9,
            1,
            null
        );
    }
}
