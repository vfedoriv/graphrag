package io.github.vfedoriv.graphrag.document.chunking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.document.ParsedSection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChunkingContractsTest {

    @Test
    void validatesContextAndSliceInvariants() {
        TokenEstimator estimator = new Utf8ByteTokenEstimator();
        ChunkingContext context = ChunkingContext.create(
            FixedCharacterChunkingStrategy.NAME,
            FixedCharacterChunkingStrategy.REVISION,
            10,
            2,
            20,
            estimator,
            "text-v1",
            "plain-text-v1"
        );

        assertThat(context.settingsHash().value()).hasSize(64);
        assertThat(context.effectiveRevision().value()).startsWith("chunker_");
        assertThatThrownBy(() -> ChunkingContext.create(
            FixedCharacterChunkingStrategy.NAME,
            FixedCharacterChunkingStrategy.REVISION,
            10,
            10,
            20,
            estimator,
            "text-v1",
            "plain-text-v1"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("smaller than targetTokens");
    }

    @Test
    void fixedStrategyPreservesCompatibilityOutputAndConstructionPositions() {
        FixedCharacterChunkingStrategy strategy = new FixedCharacterChunkingStrategy();
        ChunkingContext context = ChunkingContext.create(
            strategy.name(),
            strategy.revision(),
            800,
            2,
            10,
            new Utf8ByteTokenEstimator(),
            "text-v1",
            "plain-text-v1"
        );
        ParsedSection section = new ParsedSection(
            0,
            "  abcdefghij0123456789  ",
            "text",
            "TXT",
            null,
            null,
            Map.of()
        );

        List<ChunkSlice> slices = strategy.split(section, context);

        assertThat(slices).extracting(ChunkSlice::text)
            .containsExactly("abcdefghij", "ij01234567", "6789");
        assertThat(slices).extracting(ChunkSlice::sourceStart)
            .containsExactly(2, 10, 18);
        assertThat(slices).extracting(ChunkSlice::sourceEnd)
            .containsExactly(12, 20, 22);
        assertThat(slices).allSatisfy(slice -> {
            assertThat(slice.strategyRevision()).isEqualTo(FixedCharacterChunkingStrategy.REVISION);
            assertThat(slice.effectiveRevision()).isEqualTo(context.effectiveRevision());
        });
    }

    @Test
    void repeatedSourceTextKeepsDistinctTrackedPositions() {
        FixedCharacterChunkingStrategy strategy = new FixedCharacterChunkingStrategy();
        ChunkingContext context = ChunkingContext.create(
            strategy.name(),
            strategy.revision(),
            4,
            0,
            4,
            new Utf8ByteTokenEstimator(),
            "text-v1",
            "plain-text-v1"
        );
        ParsedSection section = new ParsedSection(0, "sameSame", "text", "TXT", null, null, Map.of());

        List<ChunkSlice> slices = strategy.split(section, context);

        assertThat(slices).extracting(ChunkSlice::text).containsExactly("same", "Same");
        assertThat(slices).extracting(ChunkSlice::sourceStart).containsExactly(0, 4);
    }
}
