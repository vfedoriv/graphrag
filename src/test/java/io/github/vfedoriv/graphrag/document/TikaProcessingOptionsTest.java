package io.github.vfedoriv.graphrag.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import static java.util.Map.entry;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.junit.jupiter.api.Test;

class TikaProcessingOptionsTest {

    private final TikaProcessingOptions options = new TikaProcessingOptions();

    @Test
    void mapsPdfOptionsIntoPerRunPdfParserConfig() {
        PDFParserConfig config = options.pdfParserConfig(Map.ofEntries(
            entry(TikaProcessingOptions.PDF_SORT_BY_POSITION, true),
            entry(TikaProcessingOptions.PDF_ENABLE_AUTO_SPACE, false),
            entry(TikaProcessingOptions.PDF_SUPPRESS_DUPLICATE_OVERLAPPING_TEXT, false),
            entry(TikaProcessingOptions.PDF_EXTRACT_ANNOTATION_TEXT, false),
            entry(TikaProcessingOptions.PDF_EXTRACT_BOOKMARKS_TEXT, false),
            entry(TikaProcessingOptions.PDF_EXTRACT_ACROFORM_CONTENT, false),
            entry(TikaProcessingOptions.PDF_EXTRACT_FONT_NAMES, true),
            entry(TikaProcessingOptions.PDF_EXTRACT_INLINE_IMAGES, true),
            entry(TikaProcessingOptions.PDF_EXTRACT_UNIQUE_INLINE_IMAGES_ONLY, false),
            entry(TikaProcessingOptions.PDF_EXTRACT_INLINE_IMAGE_METADATA_ONLY, true),
            entry(TikaProcessingOptions.PDF_OCR_STRATEGY, "OCR_ONLY"),
            entry(TikaProcessingOptions.PDF_OCR_RENDERING_STRATEGY, "ALL"),
            entry(TikaProcessingOptions.PDF_OCR_DPI, 200),
            entry(TikaProcessingOptions.PDF_OCR_IMAGE_FORMAT, "jpeg")
        ));

        assertThat(config.isSortByPosition()).isTrue();
        assertThat(config.isEnableAutoSpace()).isFalse();
        assertThat(config.isSuppressDuplicateOverlappingText()).isFalse();
        assertThat(config.isExtractAnnotationText()).isFalse();
        assertThat(config.isExtractBookmarksText()).isFalse();
        assertThat(config.isExtractAcroFormContent()).isFalse();
        assertThat(config.isExtractFontNames()).isTrue();
        assertThat(config.isExtractInlineImages()).isTrue();
        assertThat(config.isExtractUniqueInlineImagesOnly()).isFalse();
        assertThat(config.isExtractInlineImageMetadataOnly()).isTrue();
        assertThat(config.getOcrStrategy()).isEqualTo(PDFParserConfig.OCR_STRATEGY.OCR_ONLY);
        assertThat(config.getOcrRenderingStrategy()).isEqualTo(PDFParserConfig.OCR_RENDERING_STRATEGY.ALL);
        assertThat(config.getOcrDPI()).isEqualTo(200);
        assertThat(config.getOcrImageFormatName()).isEqualTo("jpeg");
    }

    @Test
    void mapsOcrOptionsIntoPerRunTesseractConfig() {
        TesseractOCRConfig config = options.tesseractOCRConfig(Map.of(
            TikaProcessingOptions.OCR_LANGUAGE, "deu",
            TikaProcessingOptions.OCR_PAGE_SEGMENTATION_MODE, "6",
            TikaProcessingOptions.OCR_PRESERVE_INTERWORD_SPACING, true,
            TikaProcessingOptions.OCR_TIMEOUT_SECONDS, 30,
            TikaProcessingOptions.OCR_SKIP, false
        ));

        assertThat(config.getLanguage()).isEqualTo("deu");
        assertThat(config.getPageSegMode()).isEqualTo("6");
        assertThat(config.isPreserveInterwordSpacing()).isTrue();
        assertThat(config.getTimeoutSeconds()).isEqualTo(30);
        assertThat(config.isSkipOcr()).isFalse();
    }

    @Test
    void createsFreshParseContextForEachRun() {
        ParseContext first = options.parseContext(Map.of(TikaProcessingOptions.PDF_SORT_BY_POSITION, true));
        ParseContext second = options.parseContext(Map.of(TikaProcessingOptions.PDF_SORT_BY_POSITION, false));

        assertThat(first).isNotSameAs(second);
        assertThat(first.get(PDFParserConfig.class)).isNotSameAs(second.get(PDFParserConfig.class));
        assertThat(first.get(PDFParserConfig.class).isSortByPosition()).isTrue();
        assertThat(second.get(PDFParserConfig.class).isSortByPosition()).isFalse();
    }

    @Test
    void mapsWriteLimitWithUnlimitedDefault() {
        assertThat(options.writeLimit(Map.of())).isEqualTo(-1);
        assertThat(options.writeLimit(Map.of(TikaProcessingOptions.TIKA_WRITE_LIMIT, 1000))).isEqualTo(1000);
    }
}
