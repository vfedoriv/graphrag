package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.dto.HybridSearchCitationKind;
import io.github.vfedoriv.graphrag.dto.HybridSearchExpandedContext;
import io.github.vfedoriv.graphrag.dto.HybridSearchGraphContext;
import io.github.vfedoriv.graphrag.dto.HybridSearchHit;
import io.github.vfedoriv.graphrag.dto.HybridSearchRetrievalEvidence;
import io.github.vfedoriv.graphrag.dto.HybridSearchSource;
import io.github.vfedoriv.graphrag.dto.HybridSearchSourceRange;
import io.github.vfedoriv.graphrag.repository.ParentContextRepository;
import io.github.vfedoriv.graphrag.repository.ParentContextRepository.AdjacentChunk;
import io.github.vfedoriv.graphrag.repository.ParentContextRepository.ParentContextRow;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ParentContextExpansionServiceTest {

    @Test
    void disabledExpansionPreservesChildOnlyHitsAndRecordsContentFreeMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ParentContextExpansionService service = new ParentContextExpansionService(
            Mockito.mock(ParentContextRepository.class),
            meterRegistry,
            ObservationRegistry.create()
        );
        RuntimeSettingsService.QuerySettings disabled = new RuntimeSettingsService.QuerySettings(
            200,
            15,
            true,
            List.of("CREATE"),
            10,
            50,
            4,
            200,
            1,
            2,
            true,
            false,
            1000,
            5,
            5,
            5,
            1
        );
        HybridSearchHit hit = hit("child-1", "doc-1");

        ParentContextExpansionService.ExpansionResult result =
            service.expand("kb-1", List.of(hit), disabled, true);

        assertThat(result.hits()).containsExactly(hit);
        assertThat(result.contexts()).isEmpty();
        assertThat(result.diagnostics().enabled()).isFalse();
        assertThat(meterRegistry.get("graphrag.search.parent_context.outcomes")
            .tag("outcome", "DISABLED").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("graphrag.search.parent_context.latency").timer().count()).isEqualTo(1L);
    }

    @Test
    void rejectsOutOfScopeParentWithoutExposingContext() {
        ParentContextExpansionService service = service();

        ParentContextExpansionService.ExpansionResult result = service.plan(
            List.of(hit("child-1", "doc-1")),
            List.of(row("child-1", "doc-1", "PARENT_SCOPE", null, 0)),
            settings(1000, 5, 5, 5),
            true,
            System.nanoTime()
        );

        assertThat(result.contexts()).isEmpty();
        assertThat(result.hits().getFirst().expandedContextId()).isNull();
        assertThat(result.diagnostics().outcomes()).containsEntry("PARENT_SCOPE", 1);
    }

    @Test
    void deduplicatesSiblingParentsWhileRetainingBothEvidenceIds() {
        ParentContextExpansionService service = service();

        ParentContextExpansionService.ExpansionResult result = service.plan(
            List.of(hit("child-1", "doc-1"), hit("child-2", "doc-1")),
            List.of(
                row("child-1", "doc-1", "VALID_PARENT", "parent-1", 100),
                row("child-2", "doc-1", "VALID_PARENT", "parent-1", 100)
            ),
            settings(1000, 5, 5, 5),
            true,
            System.nanoTime()
        );

        assertThat(result.contexts()).singleElement().satisfies(context -> {
            assertThat(context.contextChunkId()).isEqualTo("parent-1");
            assertThat(context.evidenceChunkIds()).containsExactly("child-1", "child-2");
            assertThat(context.contextText()).isEqualTo("parent text parent-1");
        });
        assertThat(result.hits()).extracting(HybridSearchHit::expandedContextId)
            .containsExactly("parent-1", "parent-1");
        assertThat(result.diagnostics().contextTokenEstimate()).isEqualTo(100);
        assertThat(result.diagnostics().outcomes()).containsEntry("DEDUPLICATED", 1);
    }

    @Test
    void appliesBudgetsInStableRankOrderWithoutChangingHits() {
        ParentContextExpansionService service = service();

        ParentContextExpansionService.ExpansionResult result = service.plan(
            List.of(hit("child-1", "doc-1"), hit("child-2", "doc-2"), hit("child-3", "doc-3")),
            List.of(
                row("child-1", "doc-1", "VALID_PARENT", "parent-1", 60),
                row("child-2", "doc-2", "VALID_PARENT", "parent-2", 50),
                row("child-3", "doc-3", "VALID_PARENT", "parent-3", 10)
            ),
            settings(100, 2, 3, 1),
            true,
            System.nanoTime()
        );

        assertThat(result.contexts()).extracting(HybridSearchExpandedContext::contextChunkId)
            .containsExactly("parent-1", "parent-3");
        assertThat(result.hits()).extracting(HybridSearchHit::chunkId)
            .containsExactly("child-1", "child-2", "child-3");
        assertThat(result.hits()).extracting(HybridSearchHit::score)
            .containsExactly(1.0, 1.0, 1.0);
        assertThat(result.diagnostics().outcomes()).containsEntry("TOKEN_BUDGET", 1);
    }

    @Test
    void buildsBoundedLegacyAdjacencyContext() {
        ParentContextExpansionService service = service();
        ParentContextRow row = row(
            "legacy",
            "doc-1",
            "NO_PARENT",
            null,
            0,
            List.of(
                adjacent("before", "before text", 10, 0, 10, 4),
                adjacent("legacy", "hit text", 20, 10, 30, 4),
                adjacent("after", "after text", 10, 30, 40, 4)
            )
        );

        ParentContextExpansionService.ExpansionResult result = service.plan(
            List.of(hit("legacy", "doc-1")),
            List.of(row),
            settings(100, 2, 2, 2),
            true,
            System.nanoTime()
        );

        assertThat(result.contexts()).singleElement().satisfies(context -> {
            assertThat(context.kind()).isEqualTo("ADJACENT");
            assertThat(context.contributingChunkIds()).containsExactly("before", "legacy", "after");
            assertThat(context.contextRange()).isEqualTo(new HybridSearchSourceRange(0, 40, 4, 4));
            assertThat(context.tokenEstimate()).isEqualTo(40);
        });
    }

    @Test
    void validatesTextAndContextCitationKinds() {
        assertThatThrownBy(() -> new HybridSearchRetrievalEvidence(
            "child",
            "text",
            new HybridSearchSourceRange(0, 4, 1, 1),
            "run",
            "revision",
            "section",
            HybridSearchCitationKind.GRAPH_PARENT
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new HybridSearchExpandedContext(
            "parent",
            "doc",
            "PARENT",
            "text",
            new HybridSearchSourceRange(0, 4, 1, 1),
            "revision",
            List.of("child"),
            List.of("parent"),
            1,
            HybridSearchCitationKind.GRAPH_PARENT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private ParentContextExpansionService service() {
        return new ParentContextExpansionService(Mockito.mock(ParentContextRepository.class));
    }

    private HybridSearchHit hit(String chunkId, String documentId) {
        return new HybridSearchHit(
            chunkId,
            documentId,
            0,
            1.0,
            "child text",
            new HybridSearchSource(documentId, "file.txt", "text/plain", 10, "{}"),
            new HybridSearchGraphContext(List.of(), List.of()),
            new HybridSearchRetrievalEvidence(
                chunkId,
                "child text",
                new HybridSearchSourceRange(0, 10, 1, 1),
                "run-1",
                "revision-1",
                "section",
                HybridSearchCitationKind.TEXT_CHILD
            ),
            null
        );
    }

    private ParentContextRow row(
        String childId,
        String documentId,
        String outcome,
        String parentId,
        int tokenEstimate
    ) {
        return row(childId, documentId, outcome, parentId, tokenEstimate, List.of());
    }

    private ParentContextRow row(
        String childId,
        String documentId,
        String outcome,
        String parentId,
        int tokenEstimate,
        List<AdjacentChunk> adjacency
    ) {
        return new ParentContextRow(
            0,
            childId,
            documentId,
            "revision-1",
            outcome,
            parentId,
            parentId == null ? null : "parent text " + parentId,
            tokenEstimate,
            0,
            100,
            1,
            2,
            adjacency
        );
    }

    private AdjacentChunk adjacent(
        String id,
        String text,
        int tokenEstimate,
        int sourceStart,
        int sourceEnd,
        int page
    ) {
        return new AdjacentChunk(id, text, tokenEstimate, sourceStart, sourceEnd, page, page);
    }

    private RuntimeSettingsService.QuerySettings settings(
        int maxTokens,
        int maxParents,
        int maxEvidence,
        int maxPerDocument
    ) {
        return new RuntimeSettingsService.QuerySettings(
            200,
            15,
            true,
            List.of("CREATE"),
            10,
            50,
            4,
            200,
            1,
            2,
            true,
            true,
            maxTokens,
            maxParents,
            maxEvidence,
            maxPerDocument,
            1
        );
    }
}
