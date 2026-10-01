package io.github.vfedoriv.graphrag.documents.ports;

import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult;

public interface DocumentGraphWriter {
    void write(String knowledgeBaseId, String extractionRunId, String schemaId, String documentId,
        String chunkId, SchemaDocument schema, GraphExtractionResult result);
}
