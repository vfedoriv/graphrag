package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.indexes.configuration.Neo4jProperties;
import io.github.vfedoriv.graphrag.ai.configuration.ModelProperties;
import io.github.vfedoriv.graphrag.storage.configuration.StorageProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ChunkingProperties;
import io.github.vfedoriv.graphrag.settings.configuration.QueryProperties;
import io.github.vfedoriv.graphrag.settings.configuration.ExtractionProperties;

import io.github.vfedoriv.graphrag.observability.configuration.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.bootstrap.AppProperties;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.ai.models.EmptyObjectProvider;
import java.nio.file.Path;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

public final class TestAiObservationService {

    private TestAiObservationService() {
    }

    public static AiObservationService noop() {
        return new AiObservationService(AiObservabilityProperties.disabled(),
            ObservationRegistry.NOOP,
            new SimpleMeterRegistry(),io.github.vfedoriv.graphrag.TestRuntimeSettings.modelMetadata(appProperties()),
            null,
            TestRuntimeSettings.from(appProperties(), AiObservabilityProperties.disabled()),
            new EmptyObjectProvider<>()
        );
    }

    private static AppProperties appProperties() {
        return new AppProperties(
            new Neo4jProperties("neo4j"),
            new ModelProperties("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
            new StorageProperties(Path.of("var/documents")),
            new ChunkingProperties(800, 80, 4000),
            new QueryProperties(200, 15, true, List.of("CREATE")),
            new ExtractionProperties(40, 80, 2)
        );
    }
}
