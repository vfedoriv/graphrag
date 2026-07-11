package io.github.vfedoriv.graphrag.application.processing;

import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.ParsedDocument;
import io.github.vfedoriv.graphrag.document.ParsedSection;
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
        ParsedDocument parsedDocument
    ) {
        List<PreparedChunk> chunks = new ArrayList<>();
        for (ParsedSection section : parsedDocument.sections()) {
            for (String text : chunkingService.split(section.text())) {
                chunks.add(new PreparedChunk(text, metadataFactory.create(document, processingRun, parsedDocument, section)));
            }
        }
        return chunks;
    }
}
