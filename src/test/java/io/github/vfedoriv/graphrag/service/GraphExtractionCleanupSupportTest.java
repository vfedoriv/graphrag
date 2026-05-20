package io.github.vfedoriv.graphrag.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphExtractionCleanupSupportTest {

    @Test
    void shouldConvertNumbersToLongAndDefaultNonNumbers() {
        assertThat(GraphExtractionCleanupSupport.toLong(10)).isEqualTo(10L);
        assertThat(GraphExtractionCleanupSupport.toLong(15.9d)).isEqualTo(15L);
        assertThat(GraphExtractionCleanupSupport.toLong("bad")).isEqualTo(0L);
        assertThat(GraphExtractionCleanupSupport.toLong(null)).isEqualTo(0L);
    }

    @Test
    void shouldChooseNonBlankErrorMessageOrExceptionSimpleName() {
        assertThat(GraphExtractionCleanupSupport.toNonBlankErrorMessage(new RuntimeException("boom"))).isEqualTo("boom");
        assertThat(GraphExtractionCleanupSupport.toNonBlankErrorMessage(new IllegalStateException(" "))).isEqualTo("IllegalStateException");
    }
}
