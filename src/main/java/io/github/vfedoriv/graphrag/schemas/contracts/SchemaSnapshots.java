package io.github.vfedoriv.graphrag.schemas.contracts;

public interface SchemaSnapshots {
    SchemaSnapshot resolveActive(String knowledgeBaseId);

    SchemaSnapshot resolveExpectedSnapshot(String knowledgeBaseId, String schemaId, String schemaContentHash);
}
