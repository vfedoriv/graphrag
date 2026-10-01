package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.domain.processing.PreparedChunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.documents.domain.chunking.RecursiveTokenAwareChunkingStrategy;
import io.github.vfedoriv.graphrag.documents.domain.chunking.Utf8ByteTokenEstimator;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.documents.adapters.graph.DocumentChunkPersistenceAdapter;
import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddingPersistenceStageTest {

    @Mock
    private ProfileScopedAiClientResolver aiClientResolver;
    @Mock
    private EmbeddingCompatibility embeddingCompatibility;
    @Mock
    private DocumentChunkPersistenceAdapter persistenceAdapter;

    @Test
    void rejectsMismatchedEmbeddingResponseBeforePersistence() {
        EmbeddingClient embeddingClient = texts -> List.of(List.of(0.1, 0.2, 0.3));
        when(aiClientResolver.embeddingClient()).thenReturn(embeddingClient);
        EmbeddingPersistenceStage stage = new EmbeddingPersistenceStage(
            aiClientResolver,
            embeddingCompatibility,
            persistenceAdapter,
            new ChunkingService(TestRuntimeSettings.from(properties())),
            new ProcessingJsonCodec(new ObjectMapper())
        );
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        document.setKnowledgeBaseId("kb-1");
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");
        profile.setBaseUrl("https://example.test");

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

    @Test
    void embedsContextualTextButPersistsOnlyAuthoritativeSourceText() {
        EmbeddingTarget space = new EmbeddingTarget("es_37f4698824be18e31dae287ad3233ad9b8ef5a30aadb4108c326a89e30a790ff", "https://example.test", "embedding", 3, "utf8-byte-v1");
        AtomicReference<List<String>> embeddedTexts = new AtomicReference<>();
        EmbeddingClient embeddingClient = texts -> {
            embeddedTexts.set(texts);
            return List.of(List.of(0.1, 0.2, 0.3));
        };
        when(aiClientResolver.embeddingClient()).thenReturn(embeddingClient);
        when(persistenceAdapter.findByDocumentId("doc-1")).thenReturn(List.of());
        EmbeddingPersistenceStage stage = new EmbeddingPersistenceStage(
            aiClientResolver,
            embeddingCompatibility,
            persistenceAdapter,
            new ChunkingService(TestRuntimeSettings.from(properties())),
            new ProcessingJsonCodec(new ObjectMapper())
        );
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        document.setKnowledgeBaseId("kb-1");
        document.setSha256("document-revision");
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");
        profile.setBaseUrl("https://example.test");
        profile.setEmbeddingModel("embedding");
        profile.setEmbeddingDimensions(3);
        ChunkingContext context = ChunkingContext.create(
            RecursiveTokenAwareChunkingStrategy.NAME,
            RecursiveTokenAwareChunkingStrategy.REVISION,
            100,
            10,
            200,
            new Utf8ByteTokenEstimator(),
            "text-v1",
            "context-header-v1"
        );
        ChunkSlice slice = new ChunkSlice(
            "source body",
            0,
            11,
            11,
            context.strategyName(),
            context.strategyRevision(),
            context.tokenEstimator().tokenizerId(),
            context.tokenEstimator().countMode(),
            context.settingsHash(),
            context.effectiveRevision(),
            Map.of()
        );
        PreparedChunk prepared = new PreparedChunk(
            "source body",
            "[context-header-v1]\nsource: contract.txt\n\nsource body",
            11,
            57,
            slice,
            Map.of("representationRevision", "context-header-v1")
        );

        stage.execute(document, profile, List.of(prepared));

        assertThat(embeddedTexts.get()).containsExactly(prepared.embeddingText());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunkNode>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceAdapter).replace(
            org.mockito.ArgumentMatchers.eq("doc-1"),
            org.mockito.ArgumentMatchers.eq("kb-1"),
            org.mockito.ArgumentMatchers.eq(space),
            chunksCaptor.capture()
        );
        assertThat(chunksCaptor.getValue()).singleElement().satisfies(chunk -> {
            assertThat(chunk.getText()).isEqualTo("source body");
            assertThat(chunk.getText()).doesNotContain("context-header");
            assertThat(chunk.getId()).startsWith("chunk_");
            assertThat(chunk.getSourceHash()).isNull();
        });
    }

    @Test
    void embedsOnlyChildrenAndPersistsParentWithoutRetrievalFields() {
        EmbeddingTarget space = new EmbeddingTarget("es_37f4698824be18e31dae287ad3233ad9b8ef5a30aadb4108c326a89e30a790ff", "https://example.test", "embedding", 3, "utf8-byte-v1");
        AtomicReference<List<String>> embeddedTexts = new AtomicReference<>();
        when(aiClientResolver.embeddingClient()).thenReturn(texts -> {
            embeddedTexts.set(texts);
            return List.of(List.of(0.1, 0.2, 0.3));
        });
        when(persistenceAdapter.findByDocumentId("doc-1")).thenReturn(List.of());
        EmbeddingPersistenceStage stage = new EmbeddingPersistenceStage(
            aiClientResolver,
            embeddingCompatibility,
            persistenceAdapter,
            new ChunkingService(TestRuntimeSettings.from(properties())),
            new ProcessingJsonCodec(new ObjectMapper())
        );
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        document.setKnowledgeBaseId("kb-1");
        document.setSha256("document-revision");
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");
        profile.setBaseUrl("https://example.test");
        profile.setEmbeddingModel("embedding");
        profile.setEmbeddingDimensions(3);
        ChunkingContext context = ChunkingContext.create(
            RecursiveTokenAwareChunkingStrategy.NAME,
            RecursiveTokenAwareChunkingStrategy.REVISION,
            100,
            10,
            200,
            new Utf8ByteTokenEstimator(),
            "text-v1",
            "context-header-v1"
        );
        ChunkSlice parentSlice = hierarchySlice(context, "PARENT", "parent text");
        String parentId = io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkIdentity.parentId(
            "document-revision", parentSlice
        );
        ChunkSlice childSlice = hierarchySlice(context, "CHILD", "child text");
        PreparedChunk parent = new PreparedChunk(
            "parent text", null, 11, 0, parentSlice, null, null, 1,
            Map.of("processingRunId", "run-1", "representationRevision", "context-header-v1")
        );
        PreparedChunk child = new PreparedChunk(
            "child text", "context child text", 10, 18, childSlice, parentId, 0, 0,
            Map.of("processingRunId", "run-1", "representationRevision", "context-header-v1")
        );

        stage.execute(document, profile, List.of(parent, child));

        assertThat(embeddedTexts.get()).containsExactly("context child text");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunkNode>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceAdapter).replace(
            org.mockito.ArgumentMatchers.eq("doc-1"),
            org.mockito.ArgumentMatchers.eq("kb-1"),
            org.mockito.ArgumentMatchers.eq(space),
            chunksCaptor.capture()
        );
        assertThat(chunksCaptor.getValue()).hasSize(2);
        assertThat(chunksCaptor.getValue().getFirst()).satisfies(node -> {
            assertThat(node.getKind()).isEqualTo("PARENT");
            assertThat(node.getId()).isEqualTo(parentId);
            assertThat(node.getEmbedding()).isNull();
            assertThat(node.getEmbeddingSpaceId()).isNull();
            assertThat(node.getChildCount()).isEqualTo(1);
        });
        assertThat(chunksCaptor.getValue().getLast()).satisfies(node -> {
            assertThat(node.getKind()).isEqualTo("CHILD");
            assertThat(node.getParentChunkId()).isEqualTo(parentId);
            assertThat(node.getChildIndex()).isZero();
            assertThat(node.getEmbedding()).hasSize(3);
        });
    }

    private ChunkSlice hierarchySlice(ChunkingContext context, String kind, String text) {
        return new ChunkSlice(
            text,
            0,
            text.length(),
            context.tokenEstimator().count(text),
            context.strategyName(),
            context.strategyRevision(),
            context.tokenEstimator().tokenizerId(),
            context.tokenEstimator().countMode(),
            context.settingsHash(),
            context.effectiveRevision(),
            kind,
            0,
            0,
            1,
            1,
            List.of("Section"),
            "AUTHORITATIVE",
            io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkHashes.sha256(text),
            Map.of()
        );
    }

    private AppProperties properties() {
        return new AppProperties(
            new AppProperties.Neo4j("neo4j"),
            new AppProperties.Model("https://api.openai.com/v1", "", "embedding", 3, "chat"),
            new AppProperties.Storage(Path.of("var/documents")),
            new AppProperties.Chunking(800, 2, 10),
            new AppProperties.Query(200, 15, true, List.of("CREATE")),
            new AppProperties.Extraction(40, 80, 2)
        );
    }
}
