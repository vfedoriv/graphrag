package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.domain.processing.PreparedChunk;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.documents.ports.DocumentChunkEffects;
import io.github.vfedoriv.graphrag.service.AiProfileContext;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class EmbeddingPersistenceStage {

    private final ProfileScopedAiClientResolver aiClientResolver;
    private final EmbeddingCompatibility embeddingCompatibility;
    private final DocumentChunkEffects persistenceAdapter;
    private final ChunkingService chunkingService;
    private final ProcessingJsonCodec jsonCodec;

    public EmbeddingPersistenceStage(
        ProfileScopedAiClientResolver aiClientResolver,
        EmbeddingCompatibility embeddingCompatibility,
        DocumentChunkEffects persistenceAdapter,
        ChunkingService chunkingService,
        ProcessingJsonCodec jsonCodec
    ) {
        this.aiClientResolver = aiClientResolver;
        this.embeddingCompatibility = embeddingCompatibility;
        this.persistenceAdapter = persistenceAdapter;
        this.chunkingService = chunkingService;
        this.jsonCodec = jsonCodec;
    }

    public List<DocumentChunkNode> execute(
        DocumentUploadNode document,
        AiProfileNode activeProfile,
        List<PreparedChunk> preparedChunks
    ) {
        EmbeddingTarget embeddingSpace = EmbeddingTarget.derive(activeProfile.getBaseUrl(), activeProfile.getEmbeddingModel(),
            activeProfile.getEmbeddingDimensions(), activeProfile.getTokenizerId() == null ? null : activeProfile.getTokenizerId().value());
        embeddingCompatibility.requireCompatible(document.getKnowledgeBaseId(), embeddingSpace);
        EmbeddingClient embeddingClient = aiClientResolver.embeddingClient();
        if (embeddingClient == null) {
            throw new IllegalStateException("Embedding model is not configured for this profile");
        }
        log.info("Embedding client resolved: documentId={}, embeddingClientClass={}",
            document.getId(), embeddingClient.getClass().getName());
        List<PreparedChunk> retrievalChunks = preparedChunks.stream()
            .filter(chunk -> !chunk.isParent())
            .toList();
        List<String> texts = retrievalChunks.stream().map(PreparedChunk::embeddingText).toList();
        List<List<Double>> embeddings = AiProfileContext.withProfile(activeProfile.getId(), () -> embeddingClient.embed(texts));
        log.info("Embedding request completed: documentId={}, vectors={}", document.getId(), embeddings.size());
        if (embeddings.size() != retrievalChunks.size()) {
            throw new IllegalStateException("Embedding response size mismatch");
        }

        persistenceAdapter.prepareRetrievalIndex(document.getKnowledgeBaseId(), embeddingSpace);
        List<DocumentChunkNode> chunks = new ArrayList<>();
        int embeddingIndex = 0;
        for (int index = 0; index < preparedChunks.size(); index++) {
            PreparedChunk preparedChunk = preparedChunks.get(index);
            List<Double> embedding = preparedChunk.isParent() ? null : embeddings.get(embeddingIndex++);
            chunks.add(toNode(document, activeProfile, embeddingSpace, preparedChunk, embedding, index));
        }
        persistenceAdapter.replace(document.getId(), document.getKnowledgeBaseId(), embeddingSpace, chunks);
        return persistenceAdapter.findByDocumentId(document.getId());
    }

    private DocumentChunkNode toNode(
        DocumentUploadNode document,
        AiProfileNode profile,
        EmbeddingTarget embeddingSpace,
        PreparedChunk preparedChunk,
        List<Double> embedding,
        int index
    ) {
        DocumentChunkNode chunk = new DocumentChunkNode();
        chunk.setId(deterministicId(document, preparedChunk));
        chunk.setKnowledgeBaseId(document.getKnowledgeBaseId());
        chunk.setDocumentId(document.getId());
        chunk.setProcessingRunId(String.valueOf(preparedChunk.metadata().get("processingRunId")));
        chunk.setChunkIndex(index);
        chunk.setText(preparedChunk.sourceText());
        chunk.setSourceText(preparedChunk.sourceText());
        chunk.setTokenEstimate(preparedChunk.tokenCount());
        chunk.setEmbeddingTokenCount(preparedChunk.embeddingTokenCount());
        if (preparedChunk.slice() != null) {
            chunk.setChunkStrategy(preparedChunk.slice().strategyName());
            chunk.setChunkStrategyRevision(preparedChunk.slice().strategyRevision());
            chunk.setChunkSettingsHash(preparedChunk.slice().settingsHash().value());
            chunk.setTokenizerId(preparedChunk.slice().tokenizerId().value());
            chunk.setTokenCountMode(preparedChunk.slice().countMode().name());
            chunk.setEffectiveChunkerRevision(preparedChunk.slice().effectiveRevision().value());
            chunk.setSourceStart(preparedChunk.slice().sourceStart());
            chunk.setSourceEnd(preparedChunk.slice().sourceEnd());
            chunk.setKind(preparedChunk.slice().kind());
            chunk.setParentChunkId(preparedChunk.parentChunkId());
            chunk.setChildIndex(preparedChunk.childIndex());
            chunk.setChildCount(preparedChunk.childCount());
            chunk.setSectionIndex(preparedChunk.slice().sectionIndex());
            chunk.setSectionChunkIndex(preparedChunk.slice().sectionChunkIndex());
            chunk.setPageStart(preparedChunk.slice().pageStart());
            chunk.setPageEnd(preparedChunk.slice().pageEnd());
            chunk.setStructuralPath(String.join(" / ", preparedChunk.slice().structuralPath()));
            chunk.setBlockConfidence(preparedChunk.slice().blockConfidence());
            chunk.setSourceHash(preparedChunk.slice().sourceHash());
            chunk.setRepresentationRevision(String.valueOf(
                preparedChunk.metadata().get("representationRevision")
            ));
        }
        if (!preparedChunk.isParent()) {
            chunk.setEmbedding(embedding);
            chunk.setEmbeddingModel(profile.getEmbeddingModel());
            chunk.setEmbeddingDimensions(profile.getEmbeddingDimensions());
            chunk.setEmbeddingSpaceId(embeddingSpace.id());
        }
        chunk.setMetadata(jsonCodec.writeMap(preparedChunk.metadata()));
        return chunk;
    }

    private String deterministicId(DocumentUploadNode document, PreparedChunk preparedChunk) {
        if (preparedChunk.slice() == null) {
            return UUID.randomUUID().toString();
        }
        if (preparedChunk.isParent()) {
            return io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkIdentity.parentId(
                document.getSha256() == null || document.getSha256().isBlank()
                    ? document.getId()
                    : document.getSha256(),
                preparedChunk.slice()
            );
        }
        return io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkIdentity.childId(
            document.getSha256() == null || document.getSha256().isBlank()
                ? document.getId()
                : document.getSha256(),
            preparedChunk.slice()
        );
    }
}
