package io.github.vfedoriv.graphrag.documents.adapters.parsing;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedBlock;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedBlockConfidence;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedBlockKind;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedDocument;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParserRevision;
import io.github.vfedoriv.graphrag.documents.domain.parsing.SourceRange;
import io.github.vfedoriv.graphrag.documents.domain.parsing.StructuralPath;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

final class StructuredTikaDocumentMapper {

    static final ParserRevision PARSER_REVISION = new ParserRevision("tika-3.2.3-structured-v1");

    private static final Pattern LIST_MARKER = Pattern.compile("^\\s*((?:\\d+[.)])|[-*\\u2022])\\s+");
    private static final Comparator<ParsedBlock> BLOCK_ORDER = Comparator
        .comparingInt((ParsedBlock block) -> block.sourceRange().startInclusive())
        .thenComparing((left, right) ->
            Integer.compare(right.sourceRange().endExclusive(), left.sourceRange().endExclusive()));

    private final TikaProcessingOptions processingOptions;

    StructuredTikaDocumentMapper(TikaProcessingOptions processingOptions) {
        this.processingOptions = processingOptions;
    }

    ParsedDocument parse(
        String filename,
        byte[] bytes,
        Map<String, Object> options,
        String format,
        boolean splitPdfPages
    ) {
        try {
            Metadata metadata = new Metadata();
            if (filename != null && !filename.isBlank()) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            }
            ToXMLContentHandler handler = new ToXMLContentHandler();
            WriteOutContentHandler limitedHandler =
                new WriteOutContentHandler(handler, processingOptions.writeLimit(options));
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(
                new ByteArrayInputStream(bytes),
                limitedHandler,
                metadata,
                processingOptions.parseContext(options)
            );
            List<ParsedSection> sections = mapXhtml(handler.toString(), format, options, splitPdfPages);
            Map<String, Object> parsedMetadata = includeMetadata(options)
                ? processingOptions.normalizedMetadata(metadata)
                : Map.of();
            return new ParsedDocument("tika", format, sections, parsedMetadata);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse structured " + format + " content with Tika", ex);
        }
    }

    List<ParsedSection> mapXhtml(
        String xhtml,
        String format,
        Map<String, Object> options,
        boolean splitPdfPages
    ) throws Exception {
        org.w3c.dom.Document document = parseXhtml(xhtml);
        return format.equals("PDF")
            ? pdfSections(document, options, splitPdfPages)
            : List.of(docxSection(document));
    }

    private org.w3c.dom.Document parseXhtml(String xhtml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(xhtml.getBytes(StandardCharsets.UTF_8)));
    }

    private ParsedSection docxSection(org.w3c.dom.Document document) {
        Element body = firstElement(document, "body");
        SectionBuilder builder = new SectionBuilder();
        List<String> headingPath = new ArrayList<>();
        if (body != null) {
            for (Element child : childElements(body)) {
                appendDocxElement(child, builder, StructuralPath.of("body"), headingPath);
            }
        }
        return builder.build(0, "tika", "DOCX", null, null);
    }

    private SourceRange appendDocxElement(
        Element element,
        SectionBuilder builder,
        StructuralPath rootPath,
        List<String> headingPath
    ) {
        String tag = tagName(element);
        if (isHeading(tag)) {
            int level = Integer.parseInt(tag.substring(1));
            while (headingPath.size() >= level) {
                headingPath.removeLast();
            }
            while (headingPath.size() < level - 1) {
                headingPath.add("heading[" + (headingPath.size() + 1) + "]");
            }
            String text = elementText(element);
            headingPath.add("heading[" + level + "]:" + pathText(text));
            return builder.appendLeaf(
                text,
                ParsedBlockKind.HEADING,
                path(rootPath, headingPath),
                ParsedBlockConfidence.AUTHORITATIVE,
                Map.of("level", level)
            );
        }
        StructuralPath currentPath = path(rootPath, headingPath);
        if (tag.equals("p")) {
            return builder.appendLeaf(
                elementText(element),
                ParsedBlockKind.PARAGRAPH,
                currentPath.append("paragraph"),
                ParsedBlockConfidence.AUTHORITATIVE,
                paragraphDiagnostics(element)
            );
        }
        if (tag.equals("table")) {
            return appendTable(element, builder, currentPath);
        }
        return builder.appendLeaf(
            elementText(element),
            ParsedBlockKind.TEXT,
            currentPath.append("unrecognized[" + tag + "]"),
            ParsedBlockConfidence.HINT,
            Map.of("sourceElement", tag)
        );
    }

    private SourceRange appendTable(Element table, SectionBuilder builder, StructuralPath path) {
        int tableIndex = builder.nextTableIndex();
        StructuralPath tablePath = path.append("table[" + tableIndex + "]");
        List<SourceRange> rowRanges = new ArrayList<>();
        int rowIndex = 0;
        for (Element descendant : descendantElements(table, "tr")) {
            rowIndex++;
            StructuralPath rowPath = tablePath.append("row[" + rowIndex + "]");
            List<SourceRange> cellRanges = new ArrayList<>();
            int cellIndex = 0;
            for (Element cell : childElements(descendant)) {
                if (!tagName(cell).equals("td") && !tagName(cell).equals("th")) {
                    continue;
                }
                cellIndex++;
                StructuralPath cellPath = rowPath.append("cell[" + cellIndex + "]");
                List<SourceRange> contentRanges = new ArrayList<>();
                for (Element child : childElements(cell)) {
                    if (tagName(child).equals("p")) {
                        contentRanges.add(builder.appendLeaf(
                            elementText(child),
                            ParsedBlockKind.PARAGRAPH,
                            cellPath.append("paragraph"),
                            ParsedBlockConfidence.AUTHORITATIVE,
                            paragraphDiagnostics(child)
                        ));
                    } else {
                        contentRanges.add(builder.appendLeaf(
                            elementText(child),
                            ParsedBlockKind.TEXT,
                            cellPath.append("unrecognized[" + tagName(child) + "]"),
                            ParsedBlockConfidence.HINT,
                            Map.of("sourceElement", tagName(child))
                        ));
                    }
                }
                if (contentRanges.isEmpty()) {
                    contentRanges.add(builder.appendLeaf(
                        elementText(cell),
                        ParsedBlockKind.TEXT,
                        cellPath,
                        ParsedBlockConfidence.HINT,
                        Map.of("sourceElement", tagName(cell))
                    ));
                }
                SourceRange cellRange = span(contentRanges);
                builder.addContainer(
                    ParsedBlockKind.TABLE_CELL,
                    cellRange,
                    cellPath,
                    ParsedBlockConfidence.AUTHORITATIVE,
                    Map.of()
                );
                cellRanges.add(cellRange);
            }
            if (!cellRanges.isEmpty()) {
                SourceRange rowRange = span(cellRanges);
                builder.addContainer(
                    ParsedBlockKind.TABLE_ROW,
                    rowRange,
                    rowPath,
                    ParsedBlockConfidence.AUTHORITATIVE,
                    Map.of()
                );
                rowRanges.add(rowRange);
            }
        }
        if (rowRanges.isEmpty()) {
            return builder.appendLeaf(
                elementText(table),
                ParsedBlockKind.TEXT,
                tablePath.append("unrecognized[table]"),
                ParsedBlockConfidence.HINT,
                Map.of("sourceElement", "table")
            );
        }
        SourceRange tableRange = span(rowRanges);
        builder.addContainer(
            ParsedBlockKind.TABLE,
            tableRange,
            tablePath,
            ParsedBlockConfidence.AUTHORITATIVE,
            Map.of()
        );
        return tableRange;
    }

    private List<ParsedSection> pdfSections(
        org.w3c.dom.Document document,
        Map<String, Object> options,
        boolean splitPages
    ) {
        List<Element> pages = new ArrayList<>();
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            if (elements.item(index) instanceof Element element && classContains(element, "page")) {
                pages.add(element);
            }
        }
        int pageCount = pages.size();
        int selectedPageCount = maxPages(options, pageCount);
        if (splitPages) {
            List<ParsedSection> sections = new ArrayList<>();
            for (int index = 0; index < selectedPageCount; index++) {
                SectionBuilder builder = new SectionBuilder();
                appendPdfPage(pages.get(index), index + 1, builder);
                sections.add(builder.build(index, "tika", "PDF", index + 1, pageCount));
            }
            return List.copyOf(sections);
        }
        SectionBuilder builder = new SectionBuilder();
        for (int index = 0; index < selectedPageCount; index++) {
            appendPdfPage(pages.get(index), index + 1, builder);
        }
        return List.of(builder.build(0, "tika", "PDF", null, null));
    }

    private void appendPdfPage(Element page, int pageNumber, SectionBuilder builder) {
        StructuralPath pagePath = StructuralPath.of("page[" + pageNumber + "]");
        List<SourceRange> contentRanges = new ArrayList<>();
        for (Element child : childElements(page)) {
            if (tagName(child).equals("p")) {
                SourceRange paragraphRange = builder.appendLeaf(
                    elementText(child),
                    ParsedBlockKind.PARAGRAPH,
                    pagePath.append("paragraph"),
                    ParsedBlockConfidence.HINT,
                    Map.of("boundarySource", "layout")
                );
                contentRanges.add(paragraphRange);
                builder.addLineHints(paragraphRange, pagePath);
            } else {
                contentRanges.add(builder.appendLeaf(
                    elementText(child),
                    ParsedBlockKind.TEXT,
                    pagePath.append("unrecognized[" + tagName(child) + "]"),
                    ParsedBlockConfidence.HINT,
                    Map.of("sourceElement", tagName(child))
                ));
            }
        }
        if (contentRanges.isEmpty()) {
            contentRanges.add(builder.appendLeaf(
                elementText(page),
                ParsedBlockKind.TEXT,
                pagePath.append("text"),
                ParsedBlockConfidence.HINT,
                Map.of()
            ));
        }
        SourceRange pageRange = span(contentRanges);
        builder.addContainer(
            ParsedBlockKind.PAGE,
            pageRange,
            pagePath,
            ParsedBlockConfidence.AUTHORITATIVE,
            Map.of("pageNumber", pageNumber)
        );
    }

    private Map<String, Object> paragraphDiagnostics(Element element) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String styleClass = element.getAttribute("class");
        if (styleClass != null && !styleClass.isBlank()) {
            metadata.put("styleClass", styleClass);
            metadata.put("styleConfidence", ParsedBlockConfidence.HINT.name());
        }
        Matcher matcher = LIST_MARKER.matcher(elementText(element));
        if (matcher.find()) {
            metadata.put("listMarker", matcher.group(1));
            metadata.put("listConfidence", ParsedBlockConfidence.HINT.name());
        }
        return metadata;
    }

    private StructuralPath path(StructuralPath root, List<String> segments) {
        StructuralPath result = root;
        for (String segment : segments) {
            result = result.append(segment);
        }
        return result;
    }

    private SourceRange span(List<SourceRange> ranges) {
        List<SourceRange> nonEmpty = ranges.stream()
            .filter(range -> range.length() > 0)
            .toList();
        if (nonEmpty.isEmpty()) {
            return new SourceRange(0, 0);
        }
        return new SourceRange(
            nonEmpty.getFirst().startInclusive(),
            nonEmpty.getLast().endExclusive()
        );
    }

    private Element firstElement(org.w3c.dom.Document document, String tag) {
        NodeList elements = document.getElementsByTagName(tag);
        return elements.getLength() == 0 ? null : (Element) elements.item(0);
    }

    private List<Element> childElements(Element parent) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element element) {
                children.add(element);
            }
        }
        return children;
    }

    private List<Element> descendantElements(Element parent, String tag) {
        List<Element> descendants = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element element) {
                descendants.add(element);
            }
        }
        return descendants;
    }

    private String elementText(Element element) {
        return cleanText(element.getTextContent());
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00a0', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .strip();
    }

    private String pathText(String text) {
        String singleLine = text.replace('\n', ' ').strip();
        return singleLine.length() <= 80 ? singleLine : singleLine.substring(0, 80);
    }

    private String tagName(Element element) {
        String tag = element.getTagName().toLowerCase(Locale.ROOT);
        int colon = tag.indexOf(':');
        return colon < 0 ? tag : tag.substring(colon + 1);
    }

    private boolean isHeading(String tag) {
        return tag.length() == 2 && tag.charAt(0) == 'h' && tag.charAt(1) >= '1' && tag.charAt(1) <= '6';
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

    private int maxPages(Map<String, Object> options, int pageCount) {
        Object value = options == null ? null : options.get("maxPages");
        if (value instanceof Number number && number.intValue() > 0) {
            return Math.min(number.intValue(), pageCount);
        }
        return pageCount;
    }

    private boolean includeMetadata(Map<String, Object> options) {
        Object value = options == null ? null : options.get("includeMetadata");
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private static final class SectionBuilder {

        private final StringBuilder text = new StringBuilder();
        private final List<ParsedBlock> blocks = new ArrayList<>();
        private int tableCount;

        private int nextTableIndex() {
            tableCount++;
            return tableCount;
        }

        private SourceRange appendLeaf(
            String value,
            ParsedBlockKind kind,
            StructuralPath path,
            ParsedBlockConfidence confidence,
            Map<String, Object> metadata
        ) {
            if (value == null || value.isBlank()) {
                return new SourceRange(text.length(), text.length());
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            int start = text.length();
            text.append(value);
            SourceRange range = new SourceRange(start, text.length());
            blocks.add(new ParsedBlock(
                kind,
                value,
                range,
                path,
                confidence,
                PARSER_REVISION,
                metadata
            ));
            return range;
        }

        private void addContainer(
            ParsedBlockKind kind,
            SourceRange range,
            StructuralPath path,
            ParsedBlockConfidence confidence,
            Map<String, Object> metadata
        ) {
            if (range.length() == 0) {
                return;
            }
            blocks.add(new ParsedBlock(
                kind,
                text.substring(range.startInclusive(), range.endExclusive()),
                range,
                path,
                confidence,
                PARSER_REVISION,
                metadata
            ));
        }

        private void addLineHints(SourceRange paragraphRange, StructuralPath pagePath) {
            String paragraphText = text.substring(
                paragraphRange.startInclusive(),
                paragraphRange.endExclusive()
            );
            int lineStart = 0;
            int lineIndex = 0;
            while (lineStart <= paragraphText.length()) {
                int newline = paragraphText.indexOf('\n', lineStart);
                int lineEnd = newline < 0 ? paragraphText.length() : newline;
                if (lineEnd > lineStart) {
                    lineIndex++;
                    SourceRange lineRange = new SourceRange(
                        paragraphRange.startInclusive() + lineStart,
                        paragraphRange.startInclusive() + lineEnd
                    );
                    addContainer(
                        ParsedBlockKind.LINE,
                        lineRange,
                        pagePath.append("line[" + lineIndex + "]"),
                        ParsedBlockConfidence.HINT,
                        Map.of("boundarySource", "layout")
                    );
                }
                if (newline < 0) {
                    break;
                }
                lineStart = newline + 1;
            }
        }

        private ParsedSection build(
            int sectionIndex,
            String parserId,
            String format,
            Integer pageNumber,
            Integer pageCount
        ) {
            blocks.sort(BLOCK_ORDER);
            return new ParsedSection(
                sectionIndex,
                text.toString(),
                parserId,
                format,
                pageNumber,
                pageCount,
                Map.of("parserRevision", PARSER_REVISION.value()),
                blocks
            );
        }
    }
}
