package io.github.vfedoriv.graphrag.ai.models;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

/** Profile-scoped client capability for governed feature model adapters. */
public interface ProfileScopedAiClientResolver extends EmbeddingClientAccess {
    EmbeddingClient embeddingClient();
    ChatModel chatModel();
    default ResolvedChatBinding chatBinding() { return ResolvedChatBinding.portable(chatModel()); }
    static ProfileScopedAiClientResolver fromProviders(ObjectProvider<EmbeddingClient> embeddings,
        ObjectProvider<ChatModel> chats, ObjectProvider<? extends AiModelAccess> models) {
        return new io.github.vfedoriv.graphrag.ai.adapters.provider.DefaultProfileScopedAiClientResolver(
            embeddings, chats, models);
    }
}
