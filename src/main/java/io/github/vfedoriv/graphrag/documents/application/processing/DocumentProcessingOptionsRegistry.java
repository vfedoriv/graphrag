package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.domain.options.DocumentFormatDetection;
import io.github.vfedoriv.graphrag.documents.domain.options.DocumentProcessingOptionConstraint;
import io.github.vfedoriv.graphrag.documents.domain.options.DocumentProcessingOptionDefinition;
import io.github.vfedoriv.graphrag.documents.domain.options.DocumentProcessingOptionValueType;

import io.github.vfedoriv.graphrag.documents.api.error.ProcessingOptionsValidationException;
import io.github.vfedoriv.graphrag.documents.adapters.parsing.TikaProcessingOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessingOptionsRegistry {

    private static final String PARSER_TEXT = "text";
    private static final String PARSER_TIKA = "tika";
    private static final String FORMAT_TXT = "TXT";
    private static final String FORMAT_PDF = "PDF";
    private static final String FORMAT_DOCX = "DOCX";

    private final List<DocumentProcessingOptionDefinition> definitions = List.of(
        new DocumentProcessingOptionDefinition(
            "preserveLineBreaks",
            "Preserve line breaks",
            DocumentProcessingOptionValueType.BOOLEAN,
            true,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TEXT, PARSER_TIKA),
            List.of(FORMAT_TXT, FORMAT_PDF, FORMAT_DOCX),
            true,
            "Preserves parser-produced line breaks before chunking."
        ),
        new DocumentProcessingOptionDefinition(
            "includeMetadata",
            "Include metadata",
            DocumentProcessingOptionValueType.BOOLEAN,
            false,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF, FORMAT_DOCX),
            true,
            "Allows parser metadata to be included in future parser-specific processing."
        ),
        new DocumentProcessingOptionDefinition(
            "ocrEnabled",
            "OCR enabled",
            DocumentProcessingOptionValueType.BOOLEAN,
            false,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Requests OCR for image-backed PDF content when a parser implementation supports it."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_SPLIT_PAGES,
            "Split PDF pages",
            DocumentProcessingOptionValueType.BOOLEAN,
            false,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Preserves PDF page boundaries as parser sections before chunking."
        ),
        new DocumentProcessingOptionDefinition(
            "maxPages",
            "Maximum pages",
            DocumentProcessingOptionValueType.INTEGER,
            0,
            DocumentProcessingOptionConstraint.integerRange(0, 10000),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maximum PDF pages to process. A value of 0 means no document-level page limit."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_SORT_BY_POSITION,
            "Sort PDF text by position",
            DocumentProcessingOptionValueType.BOOLEAN,
            false,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika PDF text position sorting for layout-sensitive extraction."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_ENABLE_AUTO_SPACE,
            "Enable PDF auto spacing",
            DocumentProcessingOptionValueType.BOOLEAN,
            true,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika PDF auto-space insertion."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_SUPPRESS_DUPLICATE_OVERLAPPING_TEXT,
            "Suppress duplicate PDF text",
            DocumentProcessingOptionValueType.BOOLEAN,
            true,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika duplicate overlapping text suppression."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_EXTRACT_ANNOTATION_TEXT,
            "Extract PDF annotations",
            DocumentProcessingOptionValueType.BOOLEAN,
            true,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika PDF annotation text extraction."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_EXTRACT_BOOKMARKS_TEXT,
            "Extract PDF bookmarks",
            DocumentProcessingOptionValueType.BOOLEAN,
            true,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika PDF bookmark text extraction."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_EXTRACT_ACROFORM_CONTENT,
            "Extract PDF forms",
            DocumentProcessingOptionValueType.BOOLEAN,
            true,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika PDF AcroForm content extraction."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_EXTRACT_FONT_NAMES,
            "Extract PDF font names",
            DocumentProcessingOptionValueType.BOOLEAN,
            false,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika PDF font-name extraction."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_EXTRACT_INLINE_IMAGES,
            "Extract PDF inline images",
            DocumentProcessingOptionValueType.BOOLEAN,
            false,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika PDF inline image extraction."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_EXTRACT_UNIQUE_INLINE_IMAGES_ONLY,
            "Extract unique PDF inline images only",
            DocumentProcessingOptionValueType.BOOLEAN,
            true,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika duplicate inline image filtering."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_EXTRACT_INLINE_IMAGE_METADATA_ONLY,
            "Extract PDF image metadata only",
            DocumentProcessingOptionValueType.BOOLEAN,
            false,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika inline image metadata-only extraction."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_OCR_STRATEGY,
            "PDF OCR strategy",
            DocumentProcessingOptionValueType.STRING,
            "NO_OCR",
            DocumentProcessingOptionConstraint.allowedValues(List.of("AUTO", "NO_OCR", "OCR_ONLY", "OCR_AND_TEXT_EXTRACTION")),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika PDF OCR strategy."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_OCR_RENDERING_STRATEGY,
            "PDF OCR rendering strategy",
            DocumentProcessingOptionValueType.STRING,
            "NO_TEXT",
            DocumentProcessingOptionConstraint.allowedValues(List.of("NO_TEXT", "TEXT_ONLY", "VECTOR_GRAPHICS_ONLY", "ALL")),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika PDF OCR rendering strategy."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_OCR_DPI,
            "PDF OCR DPI",
            DocumentProcessingOptionValueType.INTEGER,
            300,
            DocumentProcessingOptionConstraint.integerRange(72, 600),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika PDF OCR render DPI."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.PDF_OCR_IMAGE_FORMAT,
            "PDF OCR image format",
            DocumentProcessingOptionValueType.STRING,
            "png",
            DocumentProcessingOptionConstraint.allowedValues(List.of("png", "jpeg", "tiff")),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tika OCR render image format."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.OCR_LANGUAGE,
            "OCR language",
            DocumentProcessingOptionValueType.STRING,
            "eng",
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tesseract OCR language."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.OCR_PAGE_SEGMENTATION_MODE,
            "OCR page segmentation mode",
            DocumentProcessingOptionValueType.STRING,
            "1",
            DocumentProcessingOptionConstraint.allowedValues(List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13")),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tesseract page segmentation mode."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.OCR_PRESERVE_INTERWORD_SPACING,
            "Preserve OCR interword spacing",
            DocumentProcessingOptionValueType.BOOLEAN,
            false,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tesseract interword spacing preservation."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.OCR_TIMEOUT_SECONDS,
            "OCR timeout seconds",
            DocumentProcessingOptionValueType.INTEGER,
            120,
            DocumentProcessingOptionConstraint.integerRange(1, 3600),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tesseract OCR timeout."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.OCR_SKIP,
            "Skip OCR",
            DocumentProcessingOptionValueType.BOOLEAN,
            true,
            DocumentProcessingOptionConstraint.none(),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF),
            true,
            "Maps to Tesseract OCR skip flag."
        ),
        new DocumentProcessingOptionDefinition(
            TikaProcessingOptions.TIKA_WRITE_LIMIT,
            "Tika write limit",
            DocumentProcessingOptionValueType.INTEGER,
            -1,
            DocumentProcessingOptionConstraint.integerRange(-1, 10_000_000),
            List.of(PARSER_TIKA),
            List.of(FORMAT_PDF, FORMAT_DOCX),
            true,
            "Limits Tika handler output size. A value of -1 keeps Tika output unlimited."
        ),
        new DocumentProcessingOptionDefinition(
            "docxRevisionMode",
            "DOCX revision mode",
            DocumentProcessingOptionValueType.STRING,
            "final",
            DocumentProcessingOptionConstraint.allowedValues(List.of("final", "original")),
            List.of(PARSER_TIKA),
            List.of(FORMAT_DOCX),
            true,
            "Selects the preferred DOCX revision view for parser implementations that expose it."
        )
    );

    public DocumentFormatDetection detect(String filename, String contentType) {
        String filenameLower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String contentTypeLower = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (filenameLower.endsWith(".txt") || contentTypeLower.startsWith("text/plain")) {
            return new DocumentFormatDetection(PARSER_TEXT, FORMAT_TXT);
        }
        if (filenameLower.endsWith(".pdf") || contentTypeLower.contains("pdf")) {
            return new DocumentFormatDetection(PARSER_TIKA, FORMAT_PDF);
        }
        if (filenameLower.endsWith(".docx") || contentTypeLower.contains("officedocument.wordprocessingml.document")) {
            return new DocumentFormatDetection(PARSER_TIKA, FORMAT_DOCX);
        }
        throw new ProcessingOptionsValidationException(List.of("Unsupported document type. Supported formats: PDF, TXT, DOCX"));
    }

    public List<DocumentProcessingOptionDefinition> applicableDefinitions(DocumentFormatDetection detection) {
        return definitions.stream()
            .filter(definition -> definition.appliesTo(detection))
            .toList();
    }

    public Map<String, Object> builtInDefaults(DocumentFormatDetection detection) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (DocumentProcessingOptionDefinition definition : applicableDefinitions(detection)) {
            defaults.put(definition.key(), definition.defaultValue());
        }
        return defaults;
    }

    public Map<String, Object> validate(DocumentFormatDetection detection, Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return Map.of();
        }
        Map<String, DocumentProcessingOptionDefinition> applicable = new LinkedHashMap<>();
        for (DocumentProcessingOptionDefinition definition : applicableDefinitions(detection)) {
            applicable.put(definition.key(), definition);
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            DocumentProcessingOptionDefinition definition = applicable.get(entry.getKey());
            if (definition == null) {
                errors.add("Unsupported or unknown option for " + detection.fileFormat() + ": " + entry.getKey());
                continue;
            }
            normalizeValue(definition, entry.getValue(), errors).ifPresent(value -> normalized.put(entry.getKey(), value));
        }
        if (!errors.isEmpty()) {
            throw new ProcessingOptionsValidationException(errors);
        }
        return Map.copyOf(normalized);
    }

    public Map<String, Object> merge(DocumentFormatDetection detection, Map<String, Object> savedDefaults, Map<String, Object> requestOptions) {
        Map<String, Object> merged = new LinkedHashMap<>(builtInDefaults(detection));
        merged.putAll(validate(detection, savedDefaults));
        merged.putAll(validate(detection, requestOptions));
        return Map.copyOf(merged);
    }

    private java.util.Optional<Object> normalizeValue(
        DocumentProcessingOptionDefinition definition,
        Object value,
        List<String> errors
    ) {
        if (value == null) {
            errors.add(definition.key() + " must not be null");
            return java.util.Optional.empty();
        }
        Object normalized = switch (definition.valueType()) {
            case BOOLEAN -> normalizeBoolean(definition, value, errors);
            case INTEGER -> normalizeInteger(definition, value, errors);
            case STRING -> normalizeString(definition, value, errors);
        };
        if (normalized == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(normalized);
    }

    private Object normalizeBoolean(DocumentProcessingOptionDefinition definition, Object value, List<String> errors) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        errors.add(definition.key() + " must be a boolean");
        return null;
    }

    private Object normalizeInteger(DocumentProcessingOptionDefinition definition, Object value, List<String> errors) {
        if (!(value instanceof Number number)) {
            errors.add(definition.key() + " must be an integer");
            return null;
        }
        double doubleValue = number.doubleValue();
        int intValue = number.intValue();
        if (Double.compare(doubleValue, intValue) != 0) {
            errors.add(definition.key() + " must be an integer");
            return null;
        }
        Number min = definition.constraints().min();
        Number max = definition.constraints().max();
        if (min != null && intValue < min.intValue()) {
            errors.add(definition.key() + " must be greater than or equal to " + min);
        }
        if (max != null && intValue > max.intValue()) {
            errors.add(definition.key() + " must be less than or equal to " + max);
        }
        return intValue;
    }

    private Object normalizeString(DocumentProcessingOptionDefinition definition, Object value, List<String> errors) {
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            errors.add(definition.key() + " must be a non-blank string");
            return null;
        }
        List<String> allowedValues = definition.constraints().allowedValues();
        if (!allowedValues.isEmpty() && !allowedValues.contains(stringValue)) {
            errors.add(definition.key() + " must be one of " + allowedValues);
        }
        return stringValue;
    }
}
