package io.github.vfedoriv.graphrag.infrastructure.ai;

import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.service.AiProfileContext;
import io.github.vfedoriv.graphrag.service.AiRuntimeModelFactory;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Resolves the effective AI clients while honoring the active knowledge-base profile. */
@Component
public class ProfileScopedAiClientResolver {

    private final ObjectProvider<EmbeddingClient> embeddingClients;
    private final ObjectProvider<ChatModel> chatModels;
    private final ObjectProvider<AiRuntimeModelFactory> runtimeModelFactories;

    public ProfileScopedAiClientResolver(
        ObjectProvider<EmbeddingClient> embeddingClients,
        ObjectProvider<ChatModel> chatModels,
        ObjectProvider<AiRuntimeModelFactory> runtimeModelFactories
    ) {
        this.embeddingClients = embeddingClients;
        this.chatModels = chatModels;
        this.runtimeModelFactories = runtimeModelFactories;
    }

    public EmbeddingClient embeddingClient() {
        List<EmbeddingClient> clients = embeddingClients.orderedStream().toList();
        if (clients.isEmpty()) {
            return null;
        }
        if (clients.size() == 1) {
            return clients.getFirst();
        }
        return clients.stream()
            .filter(client -> !client.getClass().getName().contains("SpringAi"))
            .findFirst()
            .orElse(clients.getFirst());
    }

    public ChatModel chatModel() {
        ChatModel captured = AiProfileContext.capturedChatModel();
        if (captured != null) {
            return captured;
        }
        String profileId = AiProfileContext.activeProfileId();
        AiRuntimeModelFactory factory = runtimeModelFactories.getIfAvailable();
        if (profileId != null && factory != null) {
            return factory.chatModel(profileId);
        }
        return chatModels.getIfAvailable();
    }
}
