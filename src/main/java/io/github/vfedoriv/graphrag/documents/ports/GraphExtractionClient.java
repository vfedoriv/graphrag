package io.github.vfedoriv.graphrag.documents.ports;

import io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult;

import io.github.vfedoriv.graphrag.schema.SchemaDocument;

public interface GraphExtractionClient {

    GraphExtractionResult extract(SchemaDocument schema, String chunkText);
}
