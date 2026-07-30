package io.github.vfedoriv.graphrag.application.processing;

import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.ParsedDocument;
import io.github.vfedoriv.graphrag.document.ParsedSection;
import io.github.vfedoriv.graphrag.document.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.document.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import java.util.ArrayList;
import java.util.List;

public final class ChunkPreparationStage {

    private final ChunkingService chunkingService;
    private final ChunkMetadataFactory metadataFactory;
    private final ContextualChunkTextBuilder contextualTextBuilder;

    public ChunkPreparationStage(ChunkingService chunkingService, ChunkMetadataFactory metadataFactory) {
        this.chunkingService = chunkingService;
        this.metadataFactory = metadataFactory;
        this.contextualTextBuilder = new ContextualChunkTextBuilder();
    }

    public List<PreparedChunk> prepare(
        DocumentUploadNode document,
        DocumentProcessingRunNode processingRun,
        ParsedDocument parsedDocument,
        ChunkingContext chunkingContext
    ) {
        List<PreparedChunk> chunks = new ArrayList<>();
        for (ParsedSection section : parsedDocument.sections()) {
            for (ChunkSlice slice : chunkingService.split(section, chunkingContext)) {
                ContextualChunkTextBuilder.ContextualText contextualText = contextualTextBuilder.build(
                    document,
                    parsedDocument,
                    section,
                    slice,
                    chunkingContext
                );
                java.util.Map<String, Object> metadata =
                    new java.util.LinkedHashMap<>(metadataFactory.create(
                        document,
                        processingRun,
                        parsedDocument,
                        section,
                        slice
                    ));
                metadata.put("representationRevision", chunkingContext.representationRevision());
                metadata.put("contextualized", contextualText.contextualized());
                metadata.put("contextHeaderTokenCount", contextualText.headerTokenCount());
                chunks.add(new PreparedChunk(
                    contextualText.sourceText(),
                    contextualText.embeddingText(),
                    slice.tokenCount(),
                    chunkingContext.tokenEstimator().count(contextualText.embeddingText()),
                    slice,
                    java.util.Map.copyOf(metadata)
                ));
            }
        }
        return chunks;
    }
}
