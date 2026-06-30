package io.github.vfedoriv.graphrag.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
    }

    private byte[] pdfBytes(String... pageTexts) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (String pageText : List.of(pageTexts)) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(72, 720);
                    contentStream.showText(pageText);
                    contentStream.endText();
                }
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
