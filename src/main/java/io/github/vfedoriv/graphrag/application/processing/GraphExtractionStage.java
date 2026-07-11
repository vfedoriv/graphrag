package io.github.vfedoriv.graphrag.application.processing;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.service.AiProfileContext;
import io.github.vfedoriv.graphrag.service.GraphExtractionService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class GraphExtractionStage {

    private final GraphExtractionService graphExtractionService;

    public GraphExtractionStage(GraphExtractionService graphExtractionService) {
        this.graphExtractionService = graphExtractionService;
    }

    public void execute(
        DocumentUploadNode document,
        List<DocumentChunkNode> persistedChunks,
        AiProfileNode activeProfile,
        boolean allowOverwrite
    ) {
        log.info("Starting graph extraction: documentId={}, persistedChunks={}", document.getId(), persistedChunks.size());
        AiProfileContext.withProfile(
            activeProfile.getId(),
            () -> graphExtractionService.extract(document, persistedChunks, allowOverwrite)
        );
    }
}
