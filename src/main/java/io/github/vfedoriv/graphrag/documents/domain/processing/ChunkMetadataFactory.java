package io.github.vfedoriv.graphrag.documents.domain.processing;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedDocument;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkSlice;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChunkMetadataFactory {

    public Map<String, Object> create(
        DocumentUploadNode document,
        DocumentProcessingRunNode processingRun,
        ParsedDocument parsedDocument,
        ParsedSection section,
        ChunkSlice slice
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", document.getOriginalFilename());
        metadata.put("parserId", section.parserId() == null ? parsedDocument.parserId() : section.parserId());
        metadata.put("format", section.format() == null ? parsedDocument.format() : section.format());
        metadata.put("processingRunId", processingRun.getId());
        metadata.put("sectionIndex", section.sectionIndex());
        metadata.put("chunkStrategy", slice.strategyName());
        metadata.put("chunkStrategyRevision", slice.strategyRevision());
        metadata.put("chunkSettingsHash", slice.settingsHash().value());
        metadata.put("tokenizerId", slice.tokenizerId().value());
        metadata.put("tokenCountMode", slice.countMode().name());
        metadata.put("effectiveChunkerRevision", slice.effectiveRevision().value());
        metadata.put("kind", slice.kind());
        metadata.put("sectionChunkIndex", slice.sectionChunkIndex());
        metadata.put("sourceHash", slice.sourceHash());
        if (slice.sourceStart() != null) {
            metadata.put("sourceStart", slice.sourceStart());
            metadata.put("sourceEnd", slice.sourceEnd());
        }
        if (!slice.diagnostics().isEmpty()) {
            metadata.put("chunkDiagnostics", slice.diagnostics());
        }
        if (!slice.structuralPath().isEmpty()) {
            metadata.put("structuralPath", slice.structuralPath());
        }
        if (slice.blockConfidence() != null) {
            metadata.put("blockConfidence", slice.blockConfidence());
        }
        if (section.pageNumber() != null) {
            metadata.put("pageNumber", section.pageNumber());
        }
        if (slice.pageStart() != null) {
            metadata.put("pageStart", slice.pageStart());
            metadata.put("pageEnd", slice.pageEnd());
        }
        if (section.pageCount() != null) {
            metadata.put("pageCount", section.pageCount());
        }
        Map<String, Object> parserMetadata = new LinkedHashMap<>(parsedDocument.metadata());
        parserMetadata.putAll(section.metadata());
        if (!parserMetadata.isEmpty()) {
            metadata.put("parserMetadata", parserMetadata);
        }
        return metadata;
    }
}
