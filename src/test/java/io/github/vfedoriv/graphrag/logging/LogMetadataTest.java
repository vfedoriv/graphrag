package io.github.vfedoriv.graphrag.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogMetadataTest {

    @Test
    void lengthReturnsZeroForNull() {
        assertThat(LogMetadata.length(null)).isZero();
    }

    @Test
    void contentPreviewCollapsesWhitespaceAndTrims() {
        assertThat(LogMetadata.contentPreview("  first line\n\tsecond   line  ", 50))
            .isEqualTo("first line second line");
    }

    @Test
    void contentPreviewTruncatesAfterSanitizing() {
        assertThat(LogMetadata.contentPreview("  alpha\nbeta gamma  ", 10))
            .isEqualTo("alpha beta...");
    }

    @Test
    void contentPreviewHandlesNullAndNonPositiveLengths() {
        assertThat(LogMetadata.contentPreview(null, 10)).isEmpty();
        assertThat(LogMetadata.contentPreview("sensitive payload", 0)).isEqualTo("...");
        assertThat(LogMetadata.contentPreview("sensitive payload", -5)).isEqualTo("...");
    }

    @Test
    void fingerprintDoesNotContainSourceContent() {
        assertThat(LogMetadata.fingerprint("recognizable secret document text"))
            .matches("sha256:[0-9a-f]{16}")
            .doesNotContain("recognizable", "secret", "document");
    }
}
