package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.indexes.configuration.Neo4jProperties;
import io.github.vfedoriv.graphrag.ai.configuration.ModelProperties;
import io.github.vfedoriv.graphrag.storage.configuration.StorageProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ChunkingProperties;
import io.github.vfedoriv.graphrag.settings.configuration.QueryProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ExtractionProperties;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.bootstrap.AppProperties;
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
            new Neo4jProperties("neo4j"),
            new ModelProperties("https://api.openai.com/v1", "", "text-embedding-3-small", 3, "gpt-5-mini"),
            new StorageProperties(Path.of("var/documents")),
            new ChunkingProperties(800, overlap, maxChars),
            new QueryProperties(200, 15, true, List.of("CREATE")),
            new ExtractionProperties(40, 80, 2)
        );
    }
}
