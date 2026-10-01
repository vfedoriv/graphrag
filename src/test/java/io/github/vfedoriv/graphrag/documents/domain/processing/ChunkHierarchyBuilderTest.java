package io.github.vfedoriv.graphrag.documents.domain.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.documents.application.processing.ChunkingService;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedDocument;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkHashes;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.documents.domain.chunking.RecursiveTokenAwareChunkingStrategy;
import io.github.vfedoriv.graphrag.documents.domain.chunking.Utf8ByteTokenEstimator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChunkHierarchyBuilderTest {

    private final ChunkingContext context = ChunkingContext.create(
        RecursiveTokenAwareChunkingStrategy.NAME,
        RecursiveTokenAwareChunkingStrategy.REVISION,
        12,
        2,
        20,
        40,
        80,
        2,
        0,
        0,
        new Utf8ByteTokenEstimator(),
        "fixture-v1",
        "plain-v1"
    );

    @Test
    void materializesParentFromSourceRangeInsteadOfOverlappingChildText() {
        ParsedSection section = section(0, "one two three", null);
        ChunkingService chunkingService = mock(ChunkingService.class);
        when(chunkingService.split(section, context)).thenReturn(List.of(
            child(section, 0, 8, 0, List.of(), null),
            child(section, 4, 13, 1, List.of(), null)
        ));

        ChunkHierarchyBuilder.HierarchyPlan plan =
            new ChunkHierarchyBuilder(chunkingService::split).build(document("TXT", section), context);

        assertThat(plan.parents()).singleElement().satisfies(parent -> {
            assertThat(parent.sourceText()).isEqualTo("one two three");
            assertThat(parent.sourceText()).isNotEqualTo("one two two three");
            assertThat(parent.slice().sourceHash()).isEqualTo(ChunkHashes.sha256("one two three"));
            assertThat(parent.children()).hasSize(2);
        });
    }

    @Test
    void acceptsOnlyConsecutiveStructurallyExplicitTwoPagePdfParents() {
        ParsedSection pageOne = section(0, "continued clause", 1);
        ParsedSection pageTwo = section(1, "continues here", 2);
        ChunkingService chunkingService = mock(ChunkingService.class);
        when(chunkingService.split(pageOne, context)).thenReturn(List.of(
            child(pageOne, 0, pageOne.text().length(), 0, List.of("Clause 7"), "AUTHORITATIVE")
        ));
        when(chunkingService.split(pageTwo, context)).thenReturn(List.of(
            child(pageTwo, 0, pageTwo.text().length(), 0, List.of("Clause 7"), "AUTHORITATIVE")
        ));

        ChunkHierarchyBuilder.HierarchyPlan plan =
            new ChunkHierarchyBuilder(chunkingService::split).build(document("PDF", pageOne, pageTwo), context);

        assertThat(plan.parents()).singleElement().satisfies(parent -> {
            assertThat(parent.slice().pageStart()).isEqualTo(1);
            assertThat(parent.slice().pageEnd()).isEqualTo(2);
            assertThat(parent.sourceText()).isEqualTo("continued clause" + System.lineSeparator() + "continues here");
            assertThat(parent.children()).extracting(child -> child.slice().pageStart()).containsExactly(1, 2);
            assertThat(parent.children()).allSatisfy(child ->
                assertThat(child.slice().pageStart()).isEqualTo(child.slice().pageEnd()));
        });
    }

    @Test
    void rejectsAmbiguousCrossPageContinuity() {
        ParsedSection pageOne = section(0, "page one", 1);
        ParsedSection pageTwo = section(1, "page two", 2);
        ChunkingService chunkingService = mock(ChunkingService.class);
        when(chunkingService.split(pageOne, context)).thenReturn(List.of(
            child(pageOne, 0, pageOne.text().length(), 0, List.of(), null)
        ));
        when(chunkingService.split(pageTwo, context)).thenReturn(List.of(
            child(pageTwo, 0, pageTwo.text().length(), 0, List.of(), null)
        ));

        ChunkHierarchyBuilder.HierarchyPlan plan =
            new ChunkHierarchyBuilder(chunkingService::split).build(document("PDF", pageOne, pageTwo), context);

        assertThat(plan.parents()).hasSize(2);
        assertThat(plan.parents()).allSatisfy(parent ->
            assertThat(parent.slice().pageStart()).isEqualTo(parent.slice().pageEnd()));
    }

    @Test
    void keepsParentsWithinConfiguredTokenAndCharacterBounds() {
        ChunkingContext bounded = ChunkingContext.create(
            RecursiveTokenAwareChunkingStrategy.NAME,
            RecursiveTokenAwareChunkingStrategy.REVISION,
            8,
            0,
            8,
            8,
            8,
            1,
            0,
            0,
            new Utf8ByteTokenEstimator(),
            "fixture-v1",
            "plain-v1"
        );
        ParsedSection section = section(0, "aaaa bbbb", null);
        ChunkingService chunkingService = mock(ChunkingService.class);
        when(chunkingService.split(section, bounded)).thenReturn(List.of(
            child(bounded, section, 0, 5, 0, List.of(), null),
            child(bounded, section, 5, 9, 1, List.of(), null)
        ));

        ChunkHierarchyBuilder.HierarchyPlan plan =
            new ChunkHierarchyBuilder(chunkingService::split).build(document("TXT", section), bounded);

        assertThat(plan.parents()).hasSize(2);
        assertThat(plan.parents()).allSatisfy(parent -> {
            assertThat(parent.slice().tokenCount()).isLessThanOrEqualTo(8);
            assertThat(parent.sourceText().length()).isLessThanOrEqualTo(8);
        });
    }

    private ParsedDocument document(String format, ParsedSection... sections) {
        return new ParsedDocument("fixture", format, List.of(sections), Map.of());
    }

    private ParsedSection section(int index, String text, Integer page) {
        return new ParsedSection(index, text, "fixture", page == null ? "TXT" : "PDF", page, 2, Map.of());
    }

    private ChunkSlice child(
        ParsedSection section,
        int start,
        int end,
        int index,
        List<String> path,
        String confidence
    ) {
        return child(context, section, start, end, index, path, confidence);
    }

    private ChunkSlice child(
        ChunkingContext effectiveContext,
        ParsedSection section,
        int start,
        int end,
        int index,
        List<String> path,
        String confidence
    ) {
        String text = section.text().substring(start, end);
        return new ChunkSlice(
            text,
            start,
            end,
            effectiveContext.tokenEstimator().count(text),
            effectiveContext.strategyName(),
            effectiveContext.strategyRevision(),
            effectiveContext.tokenEstimator().tokenizerId(),
            effectiveContext.tokenEstimator().countMode(),
            effectiveContext.settingsHash(),
            effectiveContext.effectiveRevision(),
            "CHILD",
            section.sectionIndex(),
            index,
            section.pageNumber(),
            section.pageNumber(),
            path,
            confidence,
            ChunkHashes.sha256(text),
            Map.of()
        );
    }
}
