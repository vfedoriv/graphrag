package io.github.vfedoriv.graphrag.search.runs.ports;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;

/** Search-facing stored-schema availability, capture, and parsing capabilities. */
public interface SearchSchemas {
    boolean available(String schemaId);

    SchemaSnapshot capture(String schemaId);

    SchemaSnapshot resolveActive(String knowledgeBaseId);

    SchemaSnapshot parseCaptured(String knowledgeBaseId, String schemaId, String contentHash, String content);
}
