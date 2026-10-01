package io.github.vfedoriv.graphrag.documents.application.processing;

import io.github.vfedoriv.graphrag.documents.domain.processing.ChunkHierarchyBuilder;
import io.github.vfedoriv.graphrag.documents.domain.processing.ChunkMetadataFactory;
import io.github.vfedoriv.graphrag.documents.domain.processing.ContextualChunkTextBuilder;
import io.github.vfedoriv.graphrag.documents.domain.processing.PreparedChunk;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedDocument;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkIdentity;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChunkPreparationStage {

    private final ChunkMetadataFactory metadataFactory;
    private final ContextualChunkTextBuilder contextualTextBuilder;
    private final ChunkHierarchyBuilder hierarchyBuilder;

    public ChunkPreparationStage(ChunkingService chunkingService, ChunkMetadataFactory metadataFactory) {
        this.metadataFactory = metadataFactory;
        this.contextualTextBuilder = new ContextualChunkTextBuilder();
        this.hierarchyBuilder = new ChunkHierarchyBuilder(chunkingService::split);
    }

    public List<PreparedChunk> prepare(
        DocumentUploadNode document,
        DocumentProcessingRunNode processingRun,
        ParsedDocument parsedDocument,
        ChunkingContext chunkingContext
    ) {
        ChunkHierarchyBuilder.HierarchyPlan plan = hierarchyBuilder.build(parsedDocument, chunkingContext);
        if (plan.parents().isEmpty()) {
            return plan.flatChildren().stream()
                .map(child -> prepareChild(
                    document,
                    processingRun,
                    parsedDocument,
                    child.section(),
                    child.slice(),
                    chunkingContext,
                    null,
                    null,
                    0
                ))
                .toList();
        }

        List<PreparedChunk> chunks = new ArrayList<>();
        String documentRevision = documentRevision(document);
        for (ChunkHierarchyBuilder.ParentPlan parent : plan.parents()) {
            String parentId = ChunkIdentity.parentId(documentRevision, parent.slice());
            ParsedSection firstSection = parent.children().getFirst().section();
            Map<String, Object> parentMetadata = baseMetadata(
                document,
                processingRun,
                parsedDocument,
                firstSection,
                parent.slice(),
                chunkingContext
            );
            parentMetadata.put("childCount", parent.children().size());
            parentMetadata.put("hierarchyRole", "EXTRACTION_PARENT");
            chunks.add(new PreparedChunk(
                parent.sourceText(),
                null,
                parent.slice().tokenCount(),
                0,
                parent.slice(),
                null,
                null,
                parent.children().size(),
                Map.copyOf(parentMetadata)
            ));
            for (int childIndex = 0; childIndex < parent.children().size(); childIndex++) {
                ChunkHierarchyBuilder.ChildPlan child = parent.children().get(childIndex);
                chunks.add(prepareChild(
                    document,
                    processingRun,
                    parsedDocument,
                    child.section(),
                    child.slice(),
                    chunkingContext,
                    parentId,
                    childIndex,
                    parent.children().size()
                ));
            }
        }
        return List.copyOf(chunks);
    }

    private PreparedChunk prepareChild(
        DocumentUploadNode document,
        DocumentProcessingRunNode processingRun,
        ParsedDocument parsedDocument,
        ParsedSection section,
        ChunkSlice slice,
        ChunkingContext chunkingContext,
        String parentChunkId,
        Integer childIndex,
        int siblingCount
    ) {
        ContextualChunkTextBuilder.ContextualText contextualText = contextualTextBuilder.build(
            document,
            parsedDocument,
            section,
            slice,
            chunkingContext
        );
        Map<String, Object> metadata = baseMetadata(
            document,
            processingRun,
            parsedDocument,
            section,
            slice,
            chunkingContext
        );
        metadata.put("contextualized", contextualText.contextualized());
        metadata.put("contextHeaderTokenCount", contextualText.headerTokenCount());
        if (parentChunkId != null) {
            metadata.put("parentChunkId", parentChunkId);
            metadata.put("childIndex", childIndex);
            metadata.put("siblingCount", siblingCount);
        }
        return new PreparedChunk(
            contextualText.sourceText(),
            contextualText.embeddingText(),
            slice.tokenCount(),
            chunkingContext.tokenEstimator().count(contextualText.embeddingText()),
            slice,
            parentChunkId,
            childIndex,
            0,
            Map.copyOf(metadata)
        );
    }

    private Map<String, Object> baseMetadata(
        DocumentUploadNode document,
        DocumentProcessingRunNode processingRun,
        ParsedDocument parsedDocument,
        ParsedSection section,
        ChunkSlice slice,
        ChunkingContext chunkingContext
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>(metadataFactory.create(
            document,
            processingRun,
            parsedDocument,
            section,
            slice
        ));
        metadata.put("representationRevision", chunkingContext.representationRevision());
        return metadata;
    }

    private String documentRevision(DocumentUploadNode document) {
        return document.getSha256() == null || document.getSha256().isBlank()
            ? document.getId()
            : document.getSha256();
    }
}
