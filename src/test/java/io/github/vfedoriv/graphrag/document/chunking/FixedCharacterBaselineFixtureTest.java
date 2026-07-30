package io.github.vfedoriv.graphrag.document.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.document.ParsedSection;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FixedCharacterBaselineFixtureTest {

    @Test
    void fixedBaselineRemainsDeterministicAcrossBoundaryAndCostCases() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream input = getClass().getResourceAsStream(
            "/fixtures/chunking/fixed-character-v1-baseline.json"
        )) {
            BaselineFixture fixture = objectMapper.readValue(input, BaselineFixture.class);
            FixedCharacterChunkingStrategy strategy = new FixedCharacterChunkingStrategy();
            for (BaselineCase baseline : fixture.cases()) {
                ChunkingContext context = ChunkingContext.create(
                    strategy.name(),
                    strategy.revision(),
                    100,
                    baseline.overlapTokens(),
                    baseline.hardCharacterLimit(),
                    new Utf8ByteTokenEstimator(),
                    "fixture-parser-v1",
                    "plain-text-v1"
                );
                ParsedSection section =
                    new ParsedSection(0, baseline.text(), "fixture", "TEXT", null, null, Map.of());

                List<ChunkSlice> slices = strategy.split(section, context);

                assertThat(slices).as(baseline.name()).extracting(ChunkSlice::text)
                    .containsExactlyElementsOf(baseline.chunks());
                assertThat(slices).as(baseline.name()).extracting(ChunkSlice::sourceStart)
                    .containsExactlyElementsOf(baseline.sourceStarts());
                assertThat(slices.stream().mapToInt(slice -> slice.text().length()).sum())
                    .as(baseline.name() + " emitted characters")
                    .isEqualTo(baseline.emittedCharacters());
                assertThat(slices.stream().mapToInt(ChunkSlice::tokenCount).sum())
                    .as(baseline.name() + " estimated ingestion tokens")
                    .isEqualTo(baseline.estimatedTokens());
            }
        }
    }

    private record BaselineFixture(List<BaselineCase> cases) {
    }

    private record BaselineCase(
        String name,
        String text,
        int hardCharacterLimit,
        int overlapTokens,
        List<String> chunks,
        List<Integer> sourceStarts,
        int emittedCharacters,
        int estimatedTokens
    ) {
    }
}
