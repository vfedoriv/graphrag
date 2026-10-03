package io.github.vfedoriv.graphrag.ai.adapters.provider;

import io.github.vfedoriv.graphrag.ai.models.EmbeddingClient;
import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;
import io.github.vfedoriv.graphrag.ai.models.AiModelAccess;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Resolves the effective AI clients while honoring the active knowledge-base profile. */
@Component("profileScopedAiClientResolver")
public class DefaultProfileScopedAiClientResolver implements io.github.vfedoriv.graphrag.ai.models.ProfileScopedAiClientResolver {

    private final ObjectProvider<EmbeddingClient> embeddingClients;
    private final ObjectProvider<ChatModel> chatModels;
    private final ObjectProvider<? extends AiModelAccess> runtimeModelFactories;

    public DefaultProfileScopedAiClientResolver(
        ObjectProvider<EmbeddingClient> embeddingClients,
        ObjectProvider<ChatModel> chatModels,
        ObjectProvider<? extends AiModelAccess> runtimeModelFactories
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
        ChatModel captured = io.github.vfedoriv.graphrag.ai.adapters.provider.AiModelContext.capturedChatModel();
        if (captured != null) {
            return captured;
        }
        String profileId = AiProfileContext.activeProfileId();
        AiModelAccess factory = runtimeModelFactories.getIfAvailable();
        if (profileId != null && factory != null) {
            return factory.chatModel(profileId);
        }
        return chatModels.getIfAvailable();
    }
}
