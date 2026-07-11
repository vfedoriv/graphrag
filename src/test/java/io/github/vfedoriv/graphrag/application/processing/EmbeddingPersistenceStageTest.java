package io.github.vfedoriv.graphrag.application.processing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.infrastructure.persistence.DocumentChunkPersistenceAdapter;
import io.github.vfedoriv.graphrag.service.EmbeddingSpacePolicy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddingPersistenceStageTest {

    @Mock
    private ProfileScopedAiClientResolver aiClientResolver;
    @Mock
    private EmbeddingSpacePolicy embeddingSpacePolicy;
    @Mock
    private DocumentChunkPersistenceAdapter persistenceAdapter;

    @Test
    void rejectsMismatchedEmbeddingResponseBeforePersistence() {
        EmbeddingClient embeddingClient = texts -> List.of(List.of(0.1, 0.2, 0.3));
        when(aiClientResolver.embeddingClient()).thenReturn(embeddingClient);
        EmbeddingPersistenceStage stage = new EmbeddingPersistenceStage(
            aiClientResolver,
            embeddingSpacePolicy,
            persistenceAdapter,
            new ChunkingService(TestRuntimeSettings.from(properties())),
            new ProcessingJsonCodec(new ObjectMapper())
        );
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        document.setKnowledgeBaseId("kb-1");
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");

        assertThatThrownBy(() -> stage.execute(
            document,
            profile,
            List.of(new PreparedChunk("one", Map.of()), new PreparedChunk("two", Map.of()))
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Embedding response size mismatch");

        verify(persistenceAdapter, never()).replace(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyList()
        );
    }

    private AppProperties properties() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "embedding", 3, "chat"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 2, 10),
            new AppProperties.Query(200, 15, true, List.of("CREATE"), 10, 50, 4, 200, 1, 2, true),
            new AppProperties.Extraction(40, 80, 2)
        );
    }
}
