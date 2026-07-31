package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LuceneQueryCompilerTest {

    private final LuceneQueryCompiler compiler = new LuceneQueryCompiler();

    @Test
    void escapesLuceneOperatorsAndBoostsOneBoundedPhraseVariant() {
        String compiled = compiler.compile("account:ACME-42 && renewal (final)");

        assertThat(compiled)
            .contains("account\\:ACME\\-42")
            .contains("\\&\\&")
            .contains("\\(final\\)")
            .startsWith("(\"")
            .contains("\")^3 OR (");
    }

    @Test
    void boundsCompiledTerms() {
        String input = IntStream.range(0, 30).mapToObj(index -> "term" + index).collect(java.util.stream.Collectors.joining(" "));

        String compiled = compiler.compile(input);

        assertThat(compiled).contains("term15").doesNotContain("term16");
    }
}
