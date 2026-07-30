package io.github.vfedoriv.graphrag.application.processing;

import io.github.vfedoriv.graphrag.document.ParsedDocument;
import io.github.vfedoriv.graphrag.document.ParsedSection;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.document.chunking.ChunkSlice;
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
        if (slice.sourceStart() != null) {
            metadata.put("sourceStart", slice.sourceStart());
            metadata.put("sourceEnd", slice.sourceEnd());
        }
        if (!slice.diagnostics().isEmpty()) {
            metadata.put("chunkDiagnostics", slice.diagnostics());
        }
        if (section.pageNumber() != null) {
            metadata.put("pageNumber", section.pageNumber());
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
