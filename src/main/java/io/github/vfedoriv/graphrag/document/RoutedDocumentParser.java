package io.github.vfedoriv.graphrag.document;

import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RoutedDocumentParser {

    private final DocumentParser textParser = new TextDocumentParser();
    private final DocumentParser tikaParser = new ApacheTikaDocumentParser();

    public DocumentParser parserFor(String filename, String contentType) {
        String filenameLower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String contentTypeLower = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (filenameLower.endsWith(".txt") || contentTypeLower.startsWith("text/plain")) {
            log.info("Resolved document parser: filename={}, contentType={}, parser=text", filename, contentType);
            return textParser;
        }
        if (filenameLower.endsWith(".pdf") || contentTypeLower.contains("pdf")) {
            log.info("Resolved document parser: filename={}, contentType={}, parser=tika", filename, contentType);
            return tikaParser;
        }
        if (filenameLower.endsWith(".docx")
            || contentTypeLower.contains("officedocument.wordprocessingml.document")) {
            log.info("Resolved document parser: filename={}, contentType={}, parser=tika", filename, contentType);
            return tikaParser;
        }
        log.error("Unsupported document type: filename={}, contentType={}", filename, contentType);
        throw new IllegalArgumentException("Unsupported document type. Supported formats: PDF, TXT, DOCX");
    }
}
