package io.github.vfedoriv.graphrag.knowledgebase.contracts;

/** Managed knowledge-base admission, without exposing mutable association state. */
public interface ManagedKnowledgeBases {
    Facts requireManaged(String knowledgeBaseId);
    record Facts(String id, String activeSchemaId) { }
}
