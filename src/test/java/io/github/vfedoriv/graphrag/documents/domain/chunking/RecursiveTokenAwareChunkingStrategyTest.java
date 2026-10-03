package io.github.vfedoriv.graphrag.documents.domain.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedBlock;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedBlockConfidence;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedBlockKind;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParserRevision;
import io.github.vfedoriv.graphrag.documents.domain.parsing.SourceRange;
import io.github.vfedoriv.graphrag.documents.domain.parsing.StructuralPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecursiveTokenAwareChunkingStrategyTest {

    private final RecursiveTokenAwareChunkingStrategy strategy = new RecursiveTokenAwareChunkingStrategy((text, referenceContext) ->
            new io.github.vfedoriv.graphrag.documents.adapters.chunking.LangChain4jRecursiveSplitterAdapter(referenceContext)
                .referenceSegmentCount(text));

    @Test
    void keepsRepeatedTextPositionsAndDeterministicSourceHashes() {
        ParsedSection section = section("same same same", null, Map.of("language", "en"));

        List<ChunkSlice> first = strategy.split(section, context(5, 0, 20));
        List<ChunkSlice> second = strategy.split(section, context(5, 0, 20));

        assertThat(first).extracting(ChunkSlice::sourceStart).containsExactly(0, 5, 10);
        assertThat(first).extracting(ChunkSlice::sourceEnd).containsExactly(5, 10, 14);
        assertThat(first).extracting(ChunkSlice::text).containsExactly("same ", "same ", "same");
        assertThat(first).extracting(ChunkSlice::sourceHash)
            .containsExactlyElementsOf(second.stream().map(ChunkSlice::sourceHash).toList());
        assertThat(ChunkIdentity.childId("document-revision", first.getFirst()))
            .isEqualTo(ChunkIdentity.childId("document-revision", second.getFirst()))
            .startsWith("chunk_");
    }

    @Test
    void skipsEnglishSentenceTierForUnknownMultilingualText() {
        ParsedSection section = section("Привіт світе. Ще речення.", null, Map.of());

        List<ChunkSlice> slices = strategy.split(section, context(12, 0, 40));

        assertThat(slices).isNotEmpty();
        assertThat(slices).allSatisfy(slice -> {
            assertThat(slice.diagnostics()).containsEntry("knownEnglish", false);
            assertThat(slice.diagnostics().get("fallbackTier")).isNotEqualTo("sentence");
            assertThat(slice.text()).isEqualTo(
                section.text().substring(slice.sourceStart(), slice.sourceEnd())
            );
        });
    }

    @Test
    void characterFallbackBoundsTokenizerHostileUnbrokenStringsWithoutSplittingSurrogates() {
        ParsedSection section = section("abcdefghij😀klmnopqrst", null, Map.of());

        List<ChunkSlice> slices = strategy.split(section, context(6, 0, 6));

        assertThat(slices).hasSizeGreaterThan(1);
        assertThat(slices).allSatisfy(slice -> {
            assertThat(slice.text().length()).isLessThanOrEqualTo(6);
            assertThat(slice.tokenCount()).isLessThanOrEqualTo(6);
            assertThat(slice.diagnostics()).containsEntry("fallbackTier", "character");
            assertThat(slice.text()).doesNotEndWith("\uD83D");
            assertThat(slice.text()).doesNotStartWith("\uDE00");
        });
    }

    @Test
    void overlapsOnlyCompleteTrailingUnitsWithinTokenBudget() {
        ParsedSection section = section("one two three four", null, Map.of("language", "en"));

        List<ChunkSlice> slices = strategy.split(section, context(11, 6, 20));

        assertThat(slices).extracting(ChunkSlice::text)
            .containsExactly("one two ", "two three ", "three four");
        assertThat(slices).extracting(slice -> slice.diagnostics().get("overlapTokens"))
            .containsExactly(0, 4, 6);
    }

    @Test
    void retainsPageAndStructuralBlockProvenance() {
        String text = "Heading\nParagraph content";
        ParsedBlock paragraph = new ParsedBlock(
            ParsedBlockKind.PARAGRAPH,
            "Paragraph content",
            new SourceRange(8, text.length()),
            StructuralPath.of("Heading"),
            ParsedBlockConfidence.AUTHORITATIVE,
            new ParserRevision("fixture-v1"),
            Map.of()
        );
        ParsedSection section = new ParsedSection(
            2,
            text,
            "pdf",
            "PDF",
            3,
            8,
            Map.of("language", "en"),
            List.of(paragraph)
        );

        List<ChunkSlice> slices = strategy.split(section, context(50, 0, 100));

        assertThat(slices).singleElement().satisfies(slice -> {
            assertThat(slice.sectionIndex()).isEqualTo(2);
            assertThat(slice.pageStart()).isEqualTo(3);
            assertThat(slice.pageEnd()).isEqualTo(3);
            assertThat(slice.structuralPath()).containsExactly("Heading");
            assertThat(slice.blockConfidence()).isEqualTo("AUTHORITATIVE");
        });
    }

    private ParsedSection section(String text, Integer page, Map<String, Object> metadata) {
        return new ParsedSection(0, text, "fixture", "TXT", page, page, metadata);
    }

    private ChunkingContext context(int targetTokens, int overlapTokens, int hardCharacters) {
        return ChunkingContext.create(
            strategy.name(),
            strategy.revision(),
            targetTokens,
            overlapTokens,
            hardCharacters,
            new Utf8ByteTokenEstimator(),
            "fixture-v1",
            "context-header-v1"
        );
    }
}
