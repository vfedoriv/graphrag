package io.github.vfedoriv.graphrag.ai.models;

/** Local construction admission without exporting provider handles to workflows. */
public interface AiModelPreparation {
    void constructChat(String profileId);
    void constructEmbedding(String profileId);
}
