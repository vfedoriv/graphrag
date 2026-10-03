package io.github.vfedoriv.graphrag.ai.models;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/** Provider handles available exclusively to AI implementations and feature model adapters. */
public interface AiModelAccess {
    ChatModel chatModel(String profileId);
    default ResolvedChatBinding chatBinding(String profileId) {
        return ResolvedChatBinding.portable(chatModel(profileId));
    }
    EmbeddingModel embeddingModel(String profileId);
}
