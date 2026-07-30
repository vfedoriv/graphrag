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

    public ChunkPreparationStage(ChunkingService chunkingService, ChunkMetadataFactory metadataFactory) {
        this.chunkingService = chunkingService;
        this.metadataFactory = metadataFactory;
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
                chunks.add(new PreparedChunk(
                    slice.text(),
                    slice.tokenCount(),
                    slice,
                    metadataFactory.create(document, processingRun, parsedDocument, section, slice)
                ));
            }
        }
        return chunks;
    }
}
