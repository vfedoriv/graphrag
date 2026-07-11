package io.github.vfedoriv.graphrag.application.processing;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.infrastructure.persistence.DocumentChunkPersistenceAdapter;
import io.github.vfedoriv.graphrag.service.AiProfileContext;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
import io.github.vfedoriv.graphrag.service.EmbeddingSpacePolicy;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class EmbeddingPersistenceStage {

    private final ProfileScopedAiClientResolver aiClientResolver;
    private final EmbeddingSpacePolicy embeddingSpacePolicy;
    private final DocumentChunkPersistenceAdapter persistenceAdapter;
    private final ChunkingService chunkingService;
    private final ProcessingJsonCodec jsonCodec;

    public EmbeddingPersistenceStage(
        ProfileScopedAiClientResolver aiClientResolver,
        EmbeddingSpacePolicy embeddingSpacePolicy,
        DocumentChunkPersistenceAdapter persistenceAdapter,
        ChunkingService chunkingService,
        ProcessingJsonCodec jsonCodec
    ) {
        this.aiClientResolver = aiClientResolver;
        this.embeddingSpacePolicy = embeddingSpacePolicy;
        this.persistenceAdapter = persistenceAdapter;
        this.chunkingService = chunkingService;
        this.jsonCodec = jsonCodec;
    }

    public List<DocumentChunkNode> execute(
        DocumentUploadNode document,
        AiProfileNode activeProfile,
        List<PreparedChunk> preparedChunks
    ) {
        embeddingSpacePolicy.requireCompatible(document.getKnowledgeBaseId(), activeProfile);
        EmbeddingClient embeddingClient = aiClientResolver.embeddingClient();
        if (embeddingClient == null) {
            throw new IllegalStateException("Embedding model is not configured for this profile");
        }
        log.info("Embedding client resolved: documentId={}, embeddingClientClass={}",
            document.getId(), embeddingClient.getClass().getName());
        List<String> texts = preparedChunks.stream().map(PreparedChunk::text).toList();
        List<List<Double>> embeddings = AiProfileContext.withProfile(activeProfile.getId(), () -> embeddingClient.embed(texts));
        log.info("Embedding request completed: documentId={}, vectors={}", document.getId(), embeddings.size());
        if (embeddings.size() != preparedChunks.size()) {
            throw new IllegalStateException("Embedding response size mismatch");
        }

        EmbeddingSpace embeddingSpace = embeddingSpacePolicy.spaceFor(activeProfile);
        List<DocumentChunkNode> chunks = java.util.stream.IntStream.range(0, preparedChunks.size())
            .mapToObj(index -> toNode(document, activeProfile, embeddingSpace, preparedChunks.get(index), embeddings.get(index), index))
            .toList();
        persistenceAdapter.replace(document.getId(), document.getKnowledgeBaseId(), embeddingSpace, chunks);
        return persistenceAdapter.findByDocumentId(document.getId());
    }

    private DocumentChunkNode toNode(
        DocumentUploadNode document,
        AiProfileNode profile,
        EmbeddingSpace embeddingSpace,
        PreparedChunk preparedChunk,
        List<Double> embedding,
        int index
    ) {
        DocumentChunkNode chunk = new DocumentChunkNode();
        chunk.setId(UUID.randomUUID().toString());
        chunk.setDocumentId(document.getId());
        chunk.setChunkIndex(index);
        chunk.setText(preparedChunk.text());
        chunk.setTokenEstimate(chunkingService.tokenEstimate(preparedChunk.text()));
        chunk.setEmbedding(embedding);
        chunk.setEmbeddingModel(profile.getEmbeddingModel());
        chunk.setEmbeddingDimensions(profile.getEmbeddingDimensions());
        chunk.setEmbeddingSpaceId(embeddingSpace.id());
        chunk.setMetadata(jsonCodec.writeMap(preparedChunk.metadata()));
        return chunk;
    }
}
