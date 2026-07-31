package io.github.vfedoriv.graphrag.application.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.TestRuntimeSettings;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.document.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.document.chunking.RecursiveTokenAwareChunkingStrategy;
import io.github.vfedoriv.graphrag.document.chunking.Utf8ByteTokenEstimator;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.infrastructure.persistence.DocumentChunkPersistenceAdapter;
import io.github.vfedoriv.graphrag.service.EmbeddingSpacePolicy;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
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

    @Test
    void embedsContextualTextButPersistsOnlyAuthoritativeSourceText() {
        AtomicReference<List<String>> embeddedTexts = new AtomicReference<>();
        EmbeddingClient embeddingClient = texts -> {
            embeddedTexts.set(texts);
            return List.of(List.of(0.1, 0.2, 0.3));
        };
        when(aiClientResolver.embeddingClient()).thenReturn(embeddingClient);
        EmbeddingSpace space = new EmbeddingSpace("space-1", "https://example.test", "embedding", 3, "utf8-byte-v1");
        when(embeddingSpacePolicy.spaceFor(org.mockito.ArgumentMatchers.any())).thenReturn(space);
        when(persistenceAdapter.findByDocumentId("doc-1")).thenReturn(List.of());
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
        document.setSha256("document-revision");
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");
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
        AtomicReference<List<String>> embeddedTexts = new AtomicReference<>();
        when(aiClientResolver.embeddingClient()).thenReturn(texts -> {
            embeddedTexts.set(texts);
            return List.of(List.of(0.1, 0.2, 0.3));
        });
        EmbeddingSpace space = new EmbeddingSpace("space-1", "https://example.test", "embedding", 3, "utf8-byte-v1");
        when(embeddingSpacePolicy.spaceFor(org.mockito.ArgumentMatchers.any())).thenReturn(space);
        when(persistenceAdapter.findByDocumentId("doc-1")).thenReturn(List.of());
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
        document.setSha256("document-revision");
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");
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
        String parentId = io.github.vfedoriv.graphrag.document.chunking.ChunkIdentity.parentId(
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
            io.github.vfedoriv.graphrag.document.chunking.ChunkHashes.sha256(text),
            Map.of()
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
