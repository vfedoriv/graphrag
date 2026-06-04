package io.github.vfedoriv.graphrag.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

    @Test
    void lengthReturnsZeroForNull() {
        assertThat(LogSanitizer.length(null)).isZero();
    }

    @Test
    void previewCollapsesWhitespaceAndTrims() {
        assertThat(LogSanitizer.preview("  first line\n\tsecond   line  ", 50))
            .isEqualTo("first line second line");
    }

    @Test
    void previewTruncatesAfterSanitizing() {
        assertThat(LogSanitizer.preview("  alpha\nbeta gamma  ", 10))
            .isEqualTo("alpha beta...");
    }

    @Test
    void previewHandlesNullAndNonPositiveLengths() {
        assertThat(LogSanitizer.preview(null, 10)).isEmpty();
        assertThat(LogSanitizer.preview("sensitive payload", 0)).isEqualTo("...");
        assertThat(LogSanitizer.preview("sensitive payload", -5)).isEqualTo("...");
    }
}
