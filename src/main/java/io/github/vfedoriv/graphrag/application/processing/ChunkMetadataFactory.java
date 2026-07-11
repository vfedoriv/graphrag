package io.github.vfedoriv.graphrag.application.processing;

import io.github.vfedoriv.graphrag.document.ParsedDocument;
import io.github.vfedoriv.graphrag.document.ParsedSection;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChunkMetadataFactory {

    public Map<String, Object> create(
        DocumentUploadNode document,
        DocumentProcessingRunNode processingRun,
        ParsedDocument parsedDocument,
        ParsedSection section
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", document.getOriginalFilename());
        metadata.put("parserId", section.parserId() == null ? parsedDocument.parserId() : section.parserId());
        metadata.put("format", section.format() == null ? parsedDocument.format() : section.format());
        metadata.put("processingRunId", processingRun.getId());
        metadata.put("sectionIndex", section.sectionIndex());
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
