package io.github.vfedoriv.graphrag.documents.adapters.parsing;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedDocument;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;

import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RoutedDocumentParser {

    private static final String PARSER_TEXT = "text";
    private static final String PARSER_TIKA = "tika";
    private static final String FORMAT_TXT = "TXT";
    private static final String FORMAT_PDF = "PDF";
    private static final String FORMAT_DOCX = "DOCX";

    private final DocumentParser textParser = new TextDocumentParser();
    private final DocumentParser tikaParser = new ApacheTikaDocumentParser();
    private final TikaProcessingOptions tikaProcessingOptions;
    private final StructuredTikaDocumentMapper structuredTikaDocumentMapper;

    public RoutedDocumentParser(TikaProcessingOptions tikaProcessingOptions) {
        this.tikaProcessingOptions = tikaProcessingOptions;
        this.structuredTikaDocumentMapper = new StructuredTikaDocumentMapper(tikaProcessingOptions);
    }

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

    public ParsedDocument parse(
        String filename,
        String contentType,
        InputStream inputStream,
        Map<String, Object> processingOptions
    ) {
        DocumentFormat documentFormat = detect(filename, contentType);
        if (documentFormat.format().equals(FORMAT_PDF) || documentFormat.format().equals(FORMAT_DOCX)) {
            try {
                byte[] bytes = inputStream.readAllBytes();
                return structuredTikaDocumentMapper.parse(
                    filename,
                    bytes,
                    processingOptions,
                    documentFormat.format(),
                    documentFormat.format().equals(FORMAT_PDF)
                        && tikaProcessingOptions.splitPages(processingOptions)
                );
            } catch (java.io.IOException ex) {
                throw new IllegalArgumentException("Failed to read document content", ex);
            }
        }
        DocumentParser parser = parserFor(filename, contentType);
        String text = parser.parse(inputStream).text();
        ParsedSection section = new ParsedSection(
            0,
            text,
            documentFormat.parserId(),
            documentFormat.format(),
            null,
            null,
            Map.of()
        );
        return new ParsedDocument(documentFormat.parserId(), documentFormat.format(), List.of(section), Map.of());
    }

    private DocumentFormat detect(String filename, String contentType) {
        String filenameLower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String contentTypeLower = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (filenameLower.endsWith(".txt") || contentTypeLower.startsWith("text/plain")) {
            return new DocumentFormat(PARSER_TEXT, FORMAT_TXT);
        }
        if (filenameLower.endsWith(".pdf") || contentTypeLower.contains("pdf")) {
            return new DocumentFormat(PARSER_TIKA, FORMAT_PDF);
        }
        if (filenameLower.endsWith(".docx")
            || contentTypeLower.contains("officedocument.wordprocessingml.document")) {
            return new DocumentFormat(PARSER_TIKA, FORMAT_DOCX);
        }
        throw new IllegalArgumentException("Unsupported document type. Supported formats: PDF, TXT, DOCX");
    }

    private record DocumentFormat(String parserId, String format) {
    }
}
