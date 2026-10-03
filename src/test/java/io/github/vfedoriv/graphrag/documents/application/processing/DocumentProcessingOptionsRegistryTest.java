package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.domain.options.DocumentFormatDetection;
import io.github.vfedoriv.graphrag.documents.domain.options.DocumentProcessingOptionDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.documents.api.error.ProcessingOptionsValidationException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentProcessingOptionsRegistryTest {

    private final DocumentProcessingOptionsRegistry registry = new DocumentProcessingOptionsRegistry();

    @Test
    void detectsFormatsAndFiltersApplicableOptions() {
        DocumentFormatDetection txt = registry.detect("notes.txt", "text/plain");
        DocumentFormatDetection pdf = registry.detect("scan.pdf", "application/pdf");
        DocumentFormatDetection docx = registry.detect("contract.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertThat(txt.parserId()).isEqualTo("text");
        assertThat(registry.applicableDefinitions(txt)).extracting(DocumentProcessingOptionDefinition::key)
            .containsExactly("preserveLineBreaks");
        assertThat(registry.applicableDefinitions(pdf)).extracting(DocumentProcessingOptionDefinition::key)
            .contains(
                "preserveLineBreaks",
                "includeMetadata",
                "ocrEnabled",
                "pdf.split-pages",
                "maxPages",
                "pdf.sort-by-position",
                "pdf.ocr-strategy",
                "ocr.language",
                "tika.write-limit"
            );
        assertThat(registry.applicableDefinitions(docx)).extracting(DocumentProcessingOptionDefinition::key)
            .containsExactly("preserveLineBreaks", "includeMetadata", "tika.write-limit", "docxRevisionMode");
    }

    @Test
    void mergesBuiltInSavedAndRequestOptionsWithRequestPrecedence() {
        DocumentFormatDetection pdf = registry.detect("scan.pdf", "application/pdf");

        Map<String, Object> merged = registry.merge(
            pdf,
            Map.of("ocrEnabled", true, "maxPages", 10),
            Map.of("maxPages", 2)
        );

        assertThat(merged)
            .containsEntry("preserveLineBreaks", true)
            .containsEntry("includeMetadata", false)
            .containsEntry("ocrEnabled", true)
            .containsEntry("maxPages", 2)
            .containsEntry("pdf.split-pages", false)
            .containsEntry("pdf.ocr-strategy", "NO_OCR")
            .containsEntry("tika.write-limit", -1);
    }

    @Test
    void rejectsUnknownInvalidConstrainedAndUnsupportedOptions() {
        DocumentFormatDetection txt = registry.detect("notes.txt", "text/plain");
        DocumentFormatDetection pdf = registry.detect("scan.pdf", "application/pdf");
        DocumentFormatDetection docx = registry.detect("contract.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertThatThrownBy(() -> registry.validate(txt, Map.of("ocrEnabled", true)))
            .isInstanceOf(ProcessingOptionsValidationException.class)
            .hasMessage("Document processing options validation failed");
        assertThatThrownBy(() -> registry.validate(pdf, Map.of("ocrEnabled", "yes")))
            .isInstanceOf(ProcessingOptionsValidationException.class);
        assertThatThrownBy(() -> registry.validate(pdf, Map.of("maxPages", 10001)))
            .isInstanceOf(ProcessingOptionsValidationException.class);
        assertThatThrownBy(() -> registry.validate(pdf, Map.of("pdf.ocr-strategy", "tracked")))
            .isInstanceOf(ProcessingOptionsValidationException.class);
        assertThatThrownBy(() -> registry.validate(docx, Map.of("docxRevisionMode", "tracked")))
            .isInstanceOf(ProcessingOptionsValidationException.class);
    }
}
