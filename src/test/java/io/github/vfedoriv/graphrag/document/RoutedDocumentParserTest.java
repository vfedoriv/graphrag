package io.github.vfedoriv.graphrag.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.junit.jupiter.api.Test;

class RoutedDocumentParserTest {

    private final RoutedDocumentParser parser = new RoutedDocumentParser();

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
}
