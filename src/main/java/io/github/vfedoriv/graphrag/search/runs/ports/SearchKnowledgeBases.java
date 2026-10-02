package io.github.vfedoriv.graphrag.search.runs.ports;

/** Search-facing admission and immutable knowledge-base facts. */
public interface SearchKnowledgeBases {
    Facts require(String knowledgeBaseId);

    boolean exists(String knowledgeBaseId);

    record Facts(String knowledgeBaseId, String activeSchemaId, String activeAiProfileId) { }
}
