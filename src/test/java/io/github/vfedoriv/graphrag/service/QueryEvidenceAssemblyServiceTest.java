package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.dto.HybridSearchCitationKind;
import io.github.vfedoriv.graphrag.dto.HybridSearchExpandedContext;
import io.github.vfedoriv.graphrag.dto.HybridSearchGraphContext;
import io.github.vfedoriv.graphrag.dto.HybridSearchGraphEvidence;
import io.github.vfedoriv.graphrag.dto.HybridSearchHit;
import io.github.vfedoriv.graphrag.dto.HybridSearchRetrievalEvidence;
import io.github.vfedoriv.graphrag.dto.HybridSearchSource;
import io.github.vfedoriv.graphrag.dto.HybridSearchSourceRange;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryEvidenceAssemblyServiceTest {

    @Test
    void assemblesChildTextParentContextAndDeduplicatedGraphParentEvidence() {
        HybridSearchGraphEvidence graphEvidence = new HybridSearchGraphEvidence(
            "evidence-1",
            "NODE",
            "entity-1",
            "doc-1",
            "parent-1",
            "parent text",
            new HybridSearchSourceRange(0, 100, 4, 5),
            "run-1",
            "revision-1",
            HybridSearchCitationKind.GRAPH_PARENT
        );
        HybridSearchHit first = hit("child-1", graphEvidence);
        HybridSearchHit second = hit("child-2", graphEvidence);
        HybridSearchExpandedContext context = new HybridSearchExpandedContext(
            "parent-1",
            "doc-1",
            "PARENT",
            "parent text",
            new HybridSearchSourceRange(0, 100, 4, 5),
            "revision-1",
            List.of("child-1", "child-2"),
            List.of("parent-1"),
            25,
            HybridSearchCitationKind.CONTEXT_ONLY
        );

        QueryEvidenceAssemblyService.QueryEvidenceAssembly assembly =
            new QueryEvidenceAssemblyService().assemble(List.of(first, second), List.of(context));

        assertThat(assembly.hits()).extracting(hit -> hit.retrievalEvidence().citationKind())
            .containsOnly(HybridSearchCitationKind.TEXT_CHILD);
        assertThat(assembly.contexts()).singleElement()
            .satisfies(value -> assertThat(value.citationKind()).isEqualTo(HybridSearchCitationKind.CONTEXT_ONLY));
        assertThat(assembly.graphEvidence()).containsExactly(graphEvidence);
        assertThat(assembly.graphEvidence().getFirst().sourceChunkId()).isEqualTo("parent-1");
    }

    private HybridSearchHit hit(String childId, HybridSearchGraphEvidence graphEvidence) {
        return new HybridSearchHit(
            childId,
            "doc-1",
            0,
            0.9,
            "child text",
            new HybridSearchSource("doc-1", "source.txt", "text/plain", 10, "{}"),
            new HybridSearchGraphContext(List.of(), List.of(), List.of(graphEvidence)),
            new HybridSearchRetrievalEvidence(
                childId,
                "child text",
                new HybridSearchSourceRange(10, 20, 4, 4),
                "run-1",
                "revision-1",
                "Clause 7",
                HybridSearchCitationKind.TEXT_CHILD
            ),
            "parent-1"
        );
    }
}
