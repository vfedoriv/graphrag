package io.github.vfedoriv.graphrag.documents.domain.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChunkRevisionCalculatorTest {

    private final ChunkRevisionCalculator calculator = new ChunkRevisionCalculator();

    @Test
    void canonicalSettingsHashIsIndependentOfMapOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("strategy", "fixed-character");
        first.put("targetTokens", 800);
        first.put("overlapTokens", 80);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("overlapTokens", 80);
        second.put("targetTokens", 800);
        second.put("strategy", "fixed-character");

        assertThat(calculator.settingsHash(first)).isEqualTo(calculator.settingsHash(second));
    }

    @Test
    void behaviorChangeProducesDifferentRevision() {
        ChunkSettingsHash first = calculator.settingsHash(Map.of("targetTokens", 800));
        ChunkSettingsHash second = calculator.settingsHash(Map.of("targetTokens", 801));

        ChunkerRevision firstRevision = calculator.chunkerRevision(
            first, "fixed-character-v1", "cl100k-base-jtokkit-1.1.0", "text-v1", "plain-text-v1"
        );
        ChunkerRevision secondRevision = calculator.chunkerRevision(
            second, "fixed-character-v1", "cl100k-base-jtokkit-1.1.0", "text-v1", "plain-text-v1"
        );

        assertThat(secondRevision).isNotEqualTo(firstRevision);
    }
}
