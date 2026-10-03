package io.github.vfedoriv.graphrag.knowledgebase.contracts;

/** Public, non-secret knowledge-base facts needed by search workflows. */
public interface SearchKnowledgeBaseAccess {
    Facts require(String knowledgeBaseId);

    boolean exists(String knowledgeBaseId);

    record Facts(String knowledgeBaseId, String activeSchemaId, String activeAiProfileId) { }
}
