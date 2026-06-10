package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.dto.HybridSearchRequest;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.neo4j.core.Neo4jClient;

class HybridSearchServiceTest {

    @Test
    void rejectsTopKAboveConfiguredLimit() {
        HybridSearchService service = new HybridSearchService(
            props(),
            provider(texts -> List.of(vector())),
            org.mockito.Mockito.mock(Neo4jClient.class)
        );

        assertThatThrownBy(() -> service.search("kb-1", new HybridSearchRequest("contracts", 51, 1, true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("topK");
    }

    @Test
    void rejectsGraphDepthAboveConfiguredLimit() {
        HybridSearchService service = new HybridSearchService(
            props(),
            provider(texts -> List.of(vector())),
            org.mockito.Mockito.mock(Neo4jClient.class)
        );

        assertThatThrownBy(() -> service.search("kb-1", new HybridSearchRequest("contracts", 10, 3, true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("graphDepth");
    }

    @Test
    void failsClearlyWhenEmbeddingClientIsMissing() {
        HybridSearchService service = new HybridSearchService(
            props(),
            provider(),
            org.mockito.Mockito.mock(Neo4jClient.class)
        );

        assertThatThrownBy(() -> service.search("kb-1", new HybridSearchRequest("contracts", 10, 1, true)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Embedding model is not configured");
    }

    private AppProperties props() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "text-embedding-3-small", 3, "gpt-5-mini"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 80, 4000),
            new AppProperties.Query(200, 15, true, List.of("CREATE"), 10, 50, 4, 200, 1, 2, true),
            new AppProperties.Extraction(40, 80, 2)
        );
    }

    private List<Double> vector() {
        return List.of(0.1, 0.2, 0.3);
    }

    private ObjectProvider<EmbeddingClient> provider(EmbeddingClient... clients) {
        return new ObjectProvider<>() {
            @Override
            public EmbeddingClient getObject(Object... args) throws BeansException {
                return clients.length == 0 ? null : clients[0];
            }

            @Override
            public EmbeddingClient getIfAvailable() throws BeansException {
                return clients.length == 0 ? null : clients[0];
            }

            @Override
            public EmbeddingClient getIfUnique() throws BeansException {
                return clients.length == 1 ? clients[0] : null;
            }

            @Override
            public EmbeddingClient getObject() throws BeansException {
                return clients.length == 0 ? null : clients[0];
            }

            @Override
            public Stream<EmbeddingClient> orderedStream() {
                return Stream.of(clients);
            }
        };
    }
}
