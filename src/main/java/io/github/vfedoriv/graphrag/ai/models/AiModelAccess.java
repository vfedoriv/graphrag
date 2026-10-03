package io.github.vfedoriv.graphrag.ai.models;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/** Provider handles available exclusively to AI implementations and feature model adapters. */
public interface AiModelAccess {
    ChatModel chatModel(String profileId);
    EmbeddingModel embeddingModel(String profileId);
}
