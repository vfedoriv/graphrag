package io.github.vfedoriv.graphrag.documents.adapters.parsing;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.springframework.stereotype.Component;

@Component
public class TikaProcessingOptions {

    public static final String PDF_SPLIT_PAGES = "pdf.split-pages";
    public static final String PDF_SORT_BY_POSITION = "pdf.sort-by-position";
    public static final String PDF_ENABLE_AUTO_SPACE = "pdf.enable-auto-space";
    public static final String PDF_SUPPRESS_DUPLICATE_OVERLAPPING_TEXT = "pdf.suppress-duplicate-overlapping-text";
    public static final String PDF_EXTRACT_ANNOTATION_TEXT = "pdf.extract-annotation-text";
    public static final String PDF_EXTRACT_BOOKMARKS_TEXT = "pdf.extract-bookmarks-text";
    public static final String PDF_EXTRACT_ACROFORM_CONTENT = "pdf.extract-acroform-content";
    public static final String PDF_EXTRACT_FONT_NAMES = "pdf.extract-font-names";
    public static final String PDF_EXTRACT_INLINE_IMAGES = "pdf.extract-inline-images";
    public static final String PDF_EXTRACT_UNIQUE_INLINE_IMAGES_ONLY = "pdf.extract-unique-inline-images-only";
    public static final String PDF_EXTRACT_INLINE_IMAGE_METADATA_ONLY = "pdf.extract-inline-image-metadata-only";
    public static final String PDF_OCR_STRATEGY = "pdf.ocr-strategy";
    public static final String PDF_OCR_RENDERING_STRATEGY = "pdf.ocr-rendering-strategy";
    public static final String PDF_OCR_DPI = "pdf.ocr-dpi";
    public static final String PDF_OCR_IMAGE_FORMAT = "pdf.ocr-image-format";
    public static final String OCR_LANGUAGE = "ocr.language";
    public static final String OCR_PAGE_SEGMENTATION_MODE = "ocr.page-segmentation-mode";
    public static final String OCR_PRESERVE_INTERWORD_SPACING = "ocr.preserve-interword-spacing";
    public static final String OCR_TIMEOUT_SECONDS = "ocr.timeout-seconds";
    public static final String OCR_SKIP = "ocr.skip";
    public static final String TIKA_WRITE_LIMIT = "tika.write-limit";

    public ParseContext parseContext(Map<String, Object> options) {
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, pdfParserConfig(options));
        context.set(TesseractOCRConfig.class, tesseractOCRConfig(options));
        return context;
    }

    public PDFParserConfig pdfParserConfig(Map<String, Object> options) {
        Map<String, Object> safeOptions = options == null ? Map.of() : options;
        PDFParserConfig config = new PDFParserConfig();
        config.setSortByPosition(booleanOption(safeOptions, PDF_SORT_BY_POSITION, false));
        config.setEnableAutoSpace(booleanOption(safeOptions, PDF_ENABLE_AUTO_SPACE, true));
        config.setSuppressDuplicateOverlappingText(booleanOption(safeOptions, PDF_SUPPRESS_DUPLICATE_OVERLAPPING_TEXT, true));
        config.setExtractAnnotationText(booleanOption(safeOptions, PDF_EXTRACT_ANNOTATION_TEXT, true));
        config.setExtractBookmarksText(booleanOption(safeOptions, PDF_EXTRACT_BOOKMARKS_TEXT, true));
        config.setExtractAcroFormContent(booleanOption(safeOptions, PDF_EXTRACT_ACROFORM_CONTENT, true));
        config.setExtractFontNames(booleanOption(safeOptions, PDF_EXTRACT_FONT_NAMES, false));
        config.setExtractInlineImages(booleanOption(safeOptions, PDF_EXTRACT_INLINE_IMAGES, false));
        config.setExtractUniqueInlineImagesOnly(booleanOption(safeOptions, PDF_EXTRACT_UNIQUE_INLINE_IMAGES_ONLY, true));
        config.setExtractInlineImageMetadataOnly(booleanOption(safeOptions, PDF_EXTRACT_INLINE_IMAGE_METADATA_ONLY, false));
        String defaultOcrStrategy = booleanOption(safeOptions, "ocrEnabled", false) ? "AUTO" : "NO_OCR";
        config.setOcrStrategy(stringOption(safeOptions, PDF_OCR_STRATEGY, defaultOcrStrategy));
        config.setOcrRenderingStrategy(stringOption(safeOptions, PDF_OCR_RENDERING_STRATEGY, "NO_TEXT"));
        config.setOcrDPI(integerOption(safeOptions, PDF_OCR_DPI, 300));
        config.setOcrImageFormatName(stringOption(safeOptions, PDF_OCR_IMAGE_FORMAT, "png"));
        return config;
    }

    public TesseractOCRConfig tesseractOCRConfig(Map<String, Object> options) {
        Map<String, Object> safeOptions = options == null ? Map.of() : options;
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setLanguage(stringOption(safeOptions, OCR_LANGUAGE, "eng"));
        config.setPageSegMode(stringOption(safeOptions, OCR_PAGE_SEGMENTATION_MODE, "1"));
        config.setPreserveInterwordSpacing(booleanOption(safeOptions, OCR_PRESERVE_INTERWORD_SPACING, false));
        config.setTimeoutSeconds(integerOption(safeOptions, OCR_TIMEOUT_SECONDS, 120));
        boolean defaultSkipOcr = !booleanOption(safeOptions, "ocrEnabled", false);
        config.setSkipOcr(booleanOption(safeOptions, OCR_SKIP, defaultSkipOcr));
        return config;
    }

    public boolean splitPages(Map<String, Object> options) {
        return booleanOption(options == null ? Map.of() : options, PDF_SPLIT_PAGES, false);
    }

    public int writeLimit(Map<String, Object> options) {
        return integerOption(options == null ? Map.of() : options, TIKA_WRITE_LIMIT, -1);
    }

    public Map<String, Object> normalizedMetadata(org.apache.tika.metadata.Metadata metadata) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (metadata == null) {
            return normalized;
        }
        String[] names = metadata.names();
        java.util.Arrays.sort(names);
        for (String name : names) {
            String[] values = metadata.getValues(name);
            if (values.length == 1) {
                normalized.put(name, values[0]);
            } else if (values.length > 1) {
                normalized.put(name, java.util.List.of(values));
            }
        }
        return normalized;
    }

    private boolean booleanOption(Map<String, Object> options, String key, boolean defaultValue) {
        Object value = options.get(key);
        return value instanceof Boolean booleanValue ? booleanValue : defaultValue;
    }

    private int integerOption(Map<String, Object> options, String key, int defaultValue) {
        Object value = options.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private String stringOption(Map<String, Object> options, String key, String defaultValue) {
        Object value = options.get(key);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : defaultValue;
    }
}
