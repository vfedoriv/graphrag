package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.config.AiObservabilityProperties;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.service.EmptyObjectProvider;
import java.nio.file.Path;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

public final class TestAiObservationService {

    private TestAiObservationService() {
    }

    public static AiObservationService noop() {
        return new AiObservationService(
            AiObservabilityProperties.disabled(),
            ObservationRegistry.NOOP,
            new SimpleMeterRegistry(),
            appProperties(),
            null,
            TestRuntimeSettings.from(appProperties(), AiObservabilityProperties.disabled()),
            new EmptyObjectProvider<>()
        );
    }

    private static AppProperties appProperties() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 1536, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 80, 4000),
            new AppProperties.Query(200, 15, true, List.of("CREATE")),
            new AppProperties.Extraction(40, 80, 2)
        );
    }
}
