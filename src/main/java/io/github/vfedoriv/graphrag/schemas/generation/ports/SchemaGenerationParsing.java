package io.github.vfedoriv.graphrag.schemas.generation.ports;

/** Parsing supplied upload bytes for schema generation, without document state writes. */
public interface SchemaGenerationParsing {
    String parse(String filename, String contentType, byte[] bytes);
}
