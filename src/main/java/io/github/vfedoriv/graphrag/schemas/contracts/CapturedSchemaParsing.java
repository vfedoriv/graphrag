package io.github.vfedoriv.graphrag.schemas.contracts;

/** Parses a durable schema-content snapshot without resolving current registry state. */
public interface CapturedSchemaParsing {
    SchemaSnapshot parseCaptured(String knowledgeBaseId, String schemaId, String contentHash, String content);
}
