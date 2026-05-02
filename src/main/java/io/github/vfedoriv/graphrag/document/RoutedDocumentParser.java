package io.github.vfedoriv.graphrag.document;

import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RoutedDocumentParser {

    private final DocumentParser textParser = new TextDocumentParser();
    private final DocumentParser tikaParser = new ApacheTikaDocumentParser();

    public DocumentParser parserFor(String filename, String contentType) {
        String filenameLower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String contentTypeLower = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (filenameLower.endsWith(".txt") || contentTypeLower.startsWith("text/plain")) {
            return textParser;
        }
        if (filenameLower.endsWith(".pdf") || contentTypeLower.contains("pdf")) {
            return tikaParser;
        }
        if (filenameLower.endsWith(".docx")
            || contentTypeLower.contains("officedocument.wordprocessingml.document")) {
            return tikaParser;
        }
        throw new IllegalArgumentException("Unsupported document type. Supported formats: PDF, TXT, DOCX");
    }
}
