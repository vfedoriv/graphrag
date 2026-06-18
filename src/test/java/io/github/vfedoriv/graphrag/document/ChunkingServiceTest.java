package io.github.vfedoriv.graphrag.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.config.AppProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService(TestRuntimeSettings.from(props(10, 2)));

    @Test
    void splitsTextWithConfiguredOverlapAndStableOrder() {
        List<String> chunks = chunkingService.split("abcdefghij0123456789");
        assertThat(chunks).containsExactly("abcdefghij", "ij01234567", "6789");
    }

    @Test
    void tokenEstimateTracksChunkSize() {
        assertThat(chunkingService.tokenEstimate("12345678")).isEqualTo(2);
    }

    private AppProperties props(int maxChars, int overlap) {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 3, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, overlap, maxChars),
            new AppProperties.Query(200, 15, true, List.of("CREATE"), 10, 50, 4, 200, 1, 2, true),
            new AppProperties.Extraction(40, 80, 2)
        );
    }
}
