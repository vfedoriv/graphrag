package io.github.vfedoriv.graphrag.document;

import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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

    public RoutedDocumentParser(TikaProcessingOptions tikaProcessingOptions) {
        this.tikaProcessingOptions = tikaProcessingOptions;
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
        if (documentFormat.format().equals(FORMAT_PDF) && tikaProcessingOptions.splitPages(processingOptions)) {
            return parsePageAwarePdf(filename, inputStream, processingOptions, documentFormat);
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

    private ParsedDocument parsePageAwarePdf(
        String filename,
        InputStream inputStream,
        Map<String, Object> processingOptions,
        DocumentFormat documentFormat
    ) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            Metadata metadata = new Metadata();
            if (filename != null && !filename.isBlank()) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            }
            ToXMLContentHandler handler = new ToXMLContentHandler();
            WriteOutContentHandler limitedHandler = new WriteOutContentHandler(
                handler,
                tikaProcessingOptions.writeLimit(processingOptions)
            );
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(
                new ByteArrayInputStream(bytes),
                limitedHandler,
                metadata,
                tikaProcessingOptions.parseContext(processingOptions)
            );
            List<ParsedSection> sections = pageSections(handler.toString(), processingOptions, documentFormat);
            if (sections.isEmpty()) {
                String text = tikaParser.parse(new ByteArrayInputStream(bytes)).text();
                sections = List.of(new ParsedSection(0, text, documentFormat.parserId(), documentFormat.format(), null, null, Map.of()));
            }
            Map<String, Object> parsedMetadata = includeMetadata(processingOptions)
                ? tikaProcessingOptions.normalizedMetadata(metadata)
                : Map.of();
            return new ParsedDocument(documentFormat.parserId(), documentFormat.format(), sections, parsedMetadata);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse PDF with page-aware Tika processing", ex);
        }
    }

    private List<ParsedSection> pageSections(
        String xhtml,
        Map<String, Object> processingOptions,
        DocumentFormat documentFormat
    ) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        org.w3c.dom.Document document = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(xhtml.getBytes(StandardCharsets.UTF_8)));
        NodeList elements = document.getElementsByTagName("*");
        List<Element> pageElements = new ArrayList<>();
        for (int i = 0; i < elements.getLength(); i++) {
            if (elements.item(i) instanceof Element element && classContains(element, "page")) {
                pageElements.add(element);
            }
        }
        int pageCount = pageElements.size();
        int maxPages = maxPages(processingOptions, pageCount);
        List<ParsedSection> sections = new ArrayList<>();
        for (int i = 0; i < pageElements.size() && i < maxPages; i++) {
            Element element = pageElements.get(i);
            sections.add(new ParsedSection(
                i,
                element.getTextContent().strip(),
                documentFormat.parserId(),
                documentFormat.format(),
                i + 1,
                pageCount,
                Map.of()
            ));
        }
        return sections;
    }

    private int maxPages(Map<String, Object> processingOptions, int pageCount) {
        Object value = processingOptions == null ? null : processingOptions.get("maxPages");
        if (value instanceof Number number && number.intValue() > 0) {
            return Math.min(number.intValue(), pageCount);
        }
        return pageCount;
    }

    private boolean classContains(Element element, String token) {
        String className = element.getAttribute("class");
        if (className == null || className.isBlank()) {
            return false;
        }
        for (String value : className.split("\\s+")) {
            if (token.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean includeMetadata(Map<String, Object> processingOptions) {
        Object value = processingOptions == null ? null : processingOptions.get("includeMetadata");
        return value instanceof Boolean booleanValue && booleanValue;
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
