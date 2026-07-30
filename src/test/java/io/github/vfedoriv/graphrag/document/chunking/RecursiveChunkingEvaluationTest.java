package io.github.vfedoriv.graphrag.document.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.document.ParsedSection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecursiveChunkingEvaluationTest {

    @Test
    void reportsFixedBaselineVersusRecursiveBudgetAndBoundaryMetrics() throws Exception {
        List<EvaluationCase> cases = List.of(
            new EvaluationCase("prose", "First sentence stays whole. Second sentence stays whole.\n\nThird paragraph."),
            new EvaluationCase("repeated", "same value\nsame value\nsame value"),
            new EvaluationCase("multilingual", "Привіт світе. こんにちは世界。 مرحبا بالعالم."),
            new EvaluationCase("unbroken", "https://example.test/" + "identifier".repeat(20))
        );
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EvaluationCase evaluationCase : cases) {
            rows.add(evaluate(evaluationCase));
        }

        Path output = Path.of("target", "test-performance", "recursive-chunking-evaluation.json");
        Files.createDirectories(output.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), Map.of(
            "baselineStrategy", FixedCharacterChunkingStrategy.REVISION,
            "candidateStrategy", RecursiveTokenAwareChunkingStrategy.REVISION,
            "cases", rows
        ));

        assertThat(rows).allSatisfy(row -> {
            assertThat((Integer) row.get("recursiveMaxTokens")).isLessThanOrEqualTo(24);
            assertThat((Integer) row.get("recursiveMaxCharacters")).isLessThanOrEqualTo(48);
        });
        assertThat(output).exists();
    }

    private Map<String, Object> evaluate(EvaluationCase evaluationCase) {
        TokenEstimator estimator = new Utf8ByteTokenEstimator();
        FixedCharacterChunkingStrategy fixed = new FixedCharacterChunkingStrategy();
        RecursiveTokenAwareChunkingStrategy recursive = new RecursiveTokenAwareChunkingStrategy();
        ParsedSection section = new ParsedSection(
            0,
            evaluationCase.text(),
            "fixture",
            "TXT",
            null,
            null,
            Map.of("language", "en")
        );
        ChunkingContext fixedContext = ChunkingContext.create(
            fixed.name(),
            fixed.revision(),
            24,
            4,
            48,
            estimator,
            "fixture-v1",
            "plain-text-v1"
        );
        ChunkingContext recursiveContext = ChunkingContext.create(
            recursive.name(),
            recursive.revision(),
            24,
            4,
            48,
            estimator,
            "fixture-v1",
            "context-header-v1"
        );
        List<ChunkSlice> fixedChunks = fixed.split(section, fixedContext);
        List<ChunkSlice> recursiveChunks = recursive.split(section, recursiveContext);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", evaluationCase.name());
        row.put("sourceCharacters", evaluationCase.text().length());
        row.put("fixedChunks", fixedChunks.size());
        row.put("fixedMaxTokens", maxTokens(fixedChunks));
        row.put("fixedEmittedCharacters", emittedCharacters(fixedChunks));
        row.put("recursiveChunks", recursiveChunks.size());
        row.put("recursiveMaxTokens", maxTokens(recursiveChunks));
        row.put("recursiveMaxCharacters", recursiveChunks.stream().mapToInt(chunk -> chunk.text().length()).max().orElse(0));
        row.put("recursiveEmittedCharacters", emittedCharacters(recursiveChunks));
        return row;
    }

    private int maxTokens(List<ChunkSlice> chunks) {
        return chunks.stream().mapToInt(ChunkSlice::tokenCount).max().orElse(0);
    }

    private int emittedCharacters(List<ChunkSlice> chunks) {
        return chunks.stream().mapToInt(chunk -> chunk.text().length()).sum();
    }

    private record EvaluationCase(String name, String text) {
    }
}
