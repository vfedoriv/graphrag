package io.github.vfedoriv.graphrag.graph;

import io.github.vfedoriv.graphrag.schema.SchemaDocument;

public interface GraphExtractionClient {

    GraphExtractionResult extract(SchemaDocument schema, String chunkText);
}
