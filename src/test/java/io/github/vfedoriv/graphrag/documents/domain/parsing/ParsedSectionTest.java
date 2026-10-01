package io.github.vfedoriv.graphrag.documents.domain.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParsedSectionTest {

    private static final ParserRevision REVISION = new ParserRevision("test-v1");

    @Test
    void preservesAssignedRangesForRepeatedText() {
        String text = "same\nsame";
        ParsedBlock first = block("same", 0, 4);
        ParsedBlock second = block("same", 5, 9);

        ParsedSection section = new ParsedSection(
            0, text, "test", "TXT", null, null, Map.of(), List.of(first, second)
        );

        assertThat(section.blocks()).extracting(ParsedBlock::sourceRange)
            .containsExactly(new SourceRange(0, 4), new SourceRange(5, 9));
    }

    @Test
    void rejectsOutOfOrderOrUntraceableRanges() {
        assertThatThrownBy(() -> new ParsedSection(
            0,
            "alpha\nbeta",
            "test",
            "TXT",
            null,
            null,
            Map.of(),
            List.of(block("beta", 6, 10), block("alpha", 0, 5))
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ordered");

        assertThatThrownBy(() -> new ParsedSection(
            0,
            "alpha",
            "test",
            "TXT",
            null,
            null,
            Map.of(),
            List.of(block("other", 0, 5))
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("match");
    }

    private ParsedBlock block(String text, int start, int end) {
        return new ParsedBlock(
            ParsedBlockKind.TEXT,
            text,
            new SourceRange(start, end),
            StructuralPath.of("body"),
            ParsedBlockConfidence.HINT,
            REVISION,
            Map.of()
        );
    }
}
