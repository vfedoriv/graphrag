package io.github.vfedoriv.graphrag.documents.adapters.parsing;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedBlock;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedBlockConfidence;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedBlockKind;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedDocument;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import io.github.vfedoriv.graphrag.documents.domain.parsing.SourceRange;

import static io.github.vfedoriv.graphrag.document.StructuredDocumentTestFixtures.docxBytes;
import static io.github.vfedoriv.graphrag.document.StructuredDocumentTestFixtures.pdfBytes;
import static io.github.vfedoriv.graphrag.document.StructuredDocumentTestFixtures.pdfPageWithLines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.documents.domain.chunking.RecursiveTokenAwareChunkingStrategy;
import io.github.vfedoriv.graphrag.documents.domain.chunking.Utf8ByteTokenEstimator;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;

class RoutedDocumentParserTest {

    private final RoutedDocumentParser parser = new RoutedDocumentParser(new TikaProcessingOptions());

    @Test
    void routesTxtToTextParser() {
        assertThat(parser.parserFor("a.txt", "text/plain")).isInstanceOf(TextDocumentParser.class);
    }

    @Test
    void routesPdfAndDocxToTikaParser() {
        assertThat(parser.parserFor("a.pdf", "application/pdf")).isInstanceOf(ApacheTikaDocumentParser.class);
        assertThat(parser.parserFor("a.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .isInstanceOf(ApacheTikaDocumentParser.class);
    }

    @Test
    void chunksTxtDocxAndPageScopedPdfFixturesWithExactSourceRanges() throws Exception {
        ParsedDocument txt = parser.parse(
            "sample.txt",
            "text/plain",
            new ByteArrayInputStream("First paragraph.\n\nSecond paragraph.".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            Map.of()
        );
        ParsedDocument docx = parser.parse(
            "structured.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            new ByteArrayInputStream(docxBytes()),
            Map.of()
        );
        ParsedDocument pdf = parser.parse(
            "sample.pdf",
            "application/pdf",
            new ByteArrayInputStream(pdfBytes("First page text", "Second page text")),
            Map.of("pdf.split-pages", true)
        );

        assertChunkContract(txt, false);
        assertChunkContract(docx, false);
        assertChunkContract(pdf, true);
    }

    @Test
    void rejectsUnsupportedType() {
        assertThatThrownBy(() -> parser.parserFor("a.png", "image/png"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesPdfAsFlattenedDocumentByDefault() throws Exception {
        byte[] bytes = pdfBytes("First page text", "Second page text");

        ParsedDocument document = parser.parse(
            "sample.pdf",
            "application/pdf",
            new ByteArrayInputStream(bytes),
            Map.of("pdf.split-pages", false)
        );

        assertThat(document.sections()).hasSize(1);
        assertThat(document.sections().getFirst().pageNumber()).isNull();
        assertThat(document.text()).contains("First page text").contains("Second page text");
        assertThat(document.sections().getFirst().blocks())
            .filteredOn(block -> block.kind() == ParsedBlockKind.PAGE)
            .extracting(ParsedBlock::confidence)
            .containsOnly(ParsedBlockConfidence.AUTHORITATIVE);
    }

    @Test
    void parsesPdfAsOrderedPageSectionsWhenEnabled() throws Exception {
        byte[] bytes = pdfBytes("First page text", "Second page text");

        ParsedDocument document = parser.parse(
            "sample.pdf",
            "application/pdf",
            new ByteArrayInputStream(bytes),
            Map.of("pdf.split-pages", true)
        );

        assertThat(document.sections()).hasSize(2);
        assertThat(document.sections()).extracting(ParsedSection::sectionIndex).containsExactly(0, 1);
        assertThat(document.sections()).extracting(ParsedSection::pageNumber).containsExactly(1, 2);
        assertThat(document.sections()).extracting(ParsedSection::pageCount).containsExactly(2, 2);
        assertThat(document.sections()).extracting(ParsedSection::text)
            .satisfiesExactly(
                text -> assertThat(text).contains("First page text"),
                text -> assertThat(text).contains("Second page text")
            );
        assertThat(document.sections()).allSatisfy(section -> {
            assertThat(section.blocks()).anySatisfy(block -> {
                assertThat(block.kind()).isEqualTo(ParsedBlockKind.PAGE);
                assertThat(block.confidence()).isEqualTo(ParsedBlockConfidence.AUTHORITATIVE);
            });
            assertThat(section.blocks())
                .filteredOn(block -> block.kind() == ParsedBlockKind.PARAGRAPH
                    || block.kind() == ParsedBlockKind.LINE)
                .extracting(ParsedBlock::confidence)
                .containsOnly(ParsedBlockConfidence.HINT);
        });
    }

    @Test
    void mapsPinnedTikaDocxStructureWithoutSemanticInference() throws Exception {
        ParsedDocument document = parser.parse(
            "structured.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            new ByteArrayInputStream(docxBytes()),
            Map.of()
        );

        ParsedSection section = document.sections().getFirst();
        assertThat(section.text())
            .containsSubsequence(
                "Heading 1",
                "Repeated paragraph",
                "Heading 2",
                "Heading 3",
                "Heading 4",
                "Heading 5",
                "Heading 6",
                "Cell A",
                "Cell B",
                "1. Literal list marker",
                "Custom style text",
                "Repeated paragraph"
            );
        assertThat(section.blocks())
            .filteredOn(block -> block.kind() == ParsedBlockKind.HEADING)
            .hasSize(6)
            .extracting(block -> block.metadata().get("level"))
            .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(section.blocks())
            .filteredOn(block -> block.kind() == ParsedBlockKind.HEADING)
            .extracting(block -> block.structuralPath().segments())
            .containsExactly(
                List.of("body", "heading[1]:Heading 1"),
                List.of("body", "heading[1]:Heading 1", "heading[2]:Heading 2"),
                List.of("body", "heading[1]:Heading 1", "heading[2]:Heading 2", "heading[3]:Heading 3"),
                List.of(
                    "body",
                    "heading[1]:Heading 1",
                    "heading[2]:Heading 2",
                    "heading[3]:Heading 3",
                    "heading[4]:Heading 4"
                ),
                List.of(
                    "body",
                    "heading[1]:Heading 1",
                    "heading[2]:Heading 2",
                    "heading[3]:Heading 3",
                    "heading[4]:Heading 4",
                    "heading[5]:Heading 5"
                ),
                List.of(
                    "body",
                    "heading[1]:Heading 1",
                    "heading[2]:Heading 2",
                    "heading[3]:Heading 3",
                    "heading[4]:Heading 4",
                    "heading[5]:Heading 5",
                    "heading[6]:Heading 6"
                )
            );
        assertThat(section.blocks()).extracting(ParsedBlock::kind)
            .contains(ParsedBlockKind.TABLE, ParsedBlockKind.TABLE_ROW, ParsedBlockKind.TABLE_CELL)
            .doesNotContainNull();
        assertThat(section.blocks()).anySatisfy(block -> {
            assertThat(block.text()).isEqualTo("Custom style text");
            assertThat(block.kind()).isEqualTo(ParsedBlockKind.PARAGRAPH);
            assertThat(block.metadata()).containsEntry("styleClass", "custom_Callout")
                .containsEntry("styleConfidence", ParsedBlockConfidence.HINT.name());
        });
        assertTraceableContract(section);
    }

    @Test
    void preservesUnknownXhtmlTextAndRepeatedValuesInSourceOrder() throws Exception {
        StructuredTikaDocumentMapper mapper = new StructuredTikaDocumentMapper(new TikaProcessingOptions());
        String xhtml = """
            <html><body>
              <p>same</p>
              <aside>unknown value</aside>
              <p>same</p>
            </body></html>
            """;

        ParsedSection section = mapper.mapXhtml(xhtml, "DOCX", Map.of(), false).getFirst();

        assertThat(section.text()).isEqualTo("same\nunknown value\nsame");
        assertThat(section.blocks()).extracting(ParsedBlock::text)
            .containsExactly("same", "unknown value", "same");
        assertThat(section.blocks().get(1).kind()).isEqualTo(ParsedBlockKind.TEXT);
        assertThat(section.blocks().get(1).confidence()).isEqualTo(ParsedBlockConfidence.HINT);
        assertThat(section.blocks()).extracting(ParsedBlock::sourceRange)
            .containsExactly(new SourceRange(0, 4), new SourceRange(5, 18), new SourceRange(19, 23));
    }

    @Test
    void classifiesOnlyPdfPageOrderAsAuthoritative() throws Exception {
        ParsedDocument document = parser.parse(
            "ambiguous.pdf",
            "application/pdf",
            new ByteArrayInputStream(pdfPageWithLines(
                "Heading-looking text",
                "- list-looking text",
                "| table-looking text |",
                "codeLookingCall();"
            )),
            Map.of("pdf.split-pages", true)
        );

        ParsedSection section = document.sections().getFirst();
        assertThat(section.text())
            .contains("Heading-looking text")
            .contains("- list-looking text")
            .contains("| table-looking text |")
            .contains("codeLookingCall();");
        assertThat(section.blocks()).extracting(ParsedBlock::kind)
            .contains(ParsedBlockKind.PAGE, ParsedBlockKind.PARAGRAPH, ParsedBlockKind.LINE)
            .doesNotContain(
                ParsedBlockKind.HEADING,
                ParsedBlockKind.TABLE,
                ParsedBlockKind.TABLE_ROW,
                ParsedBlockKind.TABLE_CELL
            );
        assertThat(section.blocks())
            .filteredOn(block -> block.kind() == ParsedBlockKind.PAGE)
            .extracting(ParsedBlock::confidence)
            .containsOnly(ParsedBlockConfidence.AUTHORITATIVE);
        assertThat(section.blocks())
            .filteredOn(block -> block.kind() == ParsedBlockKind.PAGE)
            .extracting(block -> block.structuralPath().segments())
            .containsExactly(List.of("page[1]"));
        assertThat(section.blocks())
            .filteredOn(block -> block.kind() != ParsedBlockKind.PAGE)
            .extracting(ParsedBlock::confidence)
            .containsOnly(ParsedBlockConfidence.HINT);
        assertTraceableContract(section);
    }

    private void assertChunkContract(ParsedDocument document, boolean requirePages) {
        RecursiveTokenAwareChunkingStrategy strategy = new RecursiveTokenAwareChunkingStrategy((text, referenceContext) ->
            new io.github.vfedoriv.graphrag.documents.adapters.chunking.LangChain4jRecursiveSplitterAdapter(referenceContext)
                .referenceSegmentCount(text));
        ChunkingContext context = ChunkingContext.create(
            strategy.name(),
            strategy.revision(),
            80,
            8,
            160,
            new Utf8ByteTokenEstimator(),
            document.parserId() + "-v1",
            "context-header-v1"
        );
        List<ChunkSlice> chunks = document.sections().stream()
            .flatMap(section -> strategy.split(section, context).stream())
            .toList();

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            ParsedSection section = document.sections().get(chunk.sectionIndex());
            assertThat(chunk.text()).isEqualTo(
                section.text().substring(chunk.sourceStart(), chunk.sourceEnd())
            );
            assertThat(chunk.tokenCount()).isLessThanOrEqualTo(context.targetTokens());
            assertThat(chunk.text().length()).isLessThanOrEqualTo(context.hardCharacterLimit());
            if (requirePages) {
                assertThat(chunk.pageStart()).isEqualTo(section.pageNumber());
                assertThat(chunk.pageEnd()).isEqualTo(section.pageNumber());
            }
        });
    }

    @Test
    void pinsAcceptedTikaParserRevision() {
        assertThat(Tika.class.getPackage().getImplementationVersion()).isEqualTo("3.2.3");
        assertThat(StructuredTikaDocumentMapper.PARSER_REVISION.value())
            .isEqualTo("tika-3.2.3-structured-v1");
    }

    private void assertTraceableContract(ParsedSection section) {
        assertThat(section.blocks()).allSatisfy(block -> {
            SourceRange range = block.sourceRange();
            assertThat(section.text().substring(range.startInclusive(), range.endExclusive()))
                .isEqualTo(block.text());
            assertThat(block.parserRevision()).isEqualTo(StructuredTikaDocumentMapper.PARSER_REVISION);
            assertThat(block.structuralPath().segments()).isNotEmpty();
        });
    }

}
