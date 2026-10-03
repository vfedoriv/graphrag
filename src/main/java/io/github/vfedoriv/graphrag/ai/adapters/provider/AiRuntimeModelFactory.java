package io.github.vfedoriv.graphrag.ai.adapters.provider;

import io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService;

import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;

@Component
public class AiRuntimeModelFactory implements io.github.vfedoriv.graphrag.ai.models.AiModelCache, io.github.vfedoriv.graphrag.ai.models.AiModelAccess, io.github.vfedoriv.graphrag.ai.models.AiModelPreparation {

    private final AiProfileService aiProfileService;
    private final Map<String, org.springframework.ai.chat.model.ChatModel> chatCache = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingCache = new ConcurrentHashMap<>();

    public AiRuntimeModelFactory(AiProfileService aiProfileService) {
        this.aiProfileService = aiProfileService;
    }

    public org.springframework.ai.chat.model.ChatModel chatModel(String profileId) {
        return chatBinding(profileId).model();
    }

    @Override
    public io.github.vfedoriv.graphrag.ai.models.ResolvedChatBinding chatBinding(String profileId) {
        AiProfileNode profile = aiProfileService.getNode(profileId);
        String cacheKey = cacheKey(profile, "chat");
        org.springframework.ai.chat.model.ChatModel model = chatCache.computeIfAbsent(cacheKey, ignored -> buildChatModel(profile));
        return new io.github.vfedoriv.graphrag.ai.models.ResolvedChatBinding(model, profile.getStructuredOutputMode(), profile.getId(), profile.getRevision());
    }

    public EmbeddingModel embeddingModel(String profileId) {
        AiProfileNode profile = aiProfileService.getNode(profileId);
        String cacheKey = cacheKey(profile, "embedding");
        return embeddingCache.computeIfAbsent(cacheKey, ignored -> buildEmbeddingModel(profile));
    }

    public void invalidate(String profileId) {
        chatCache.keySet().removeIf(key -> key.startsWith(profileId + ":"));
        embeddingCache.keySet().removeIf(key -> key.startsWith(profileId + ":"));
    }

    private org.springframework.ai.chat.model.ChatModel buildChatModel(AiProfileNode profile) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .baseUrl(profile.getBaseUrl())
            .apiKey(profile.getApiKey() == null ? "" : profile.getApiKey())
            .model(profile.getChatModel())
            .timeout(Duration.ofSeconds(profile.getTimeoutSeconds()))
            .maxRetries(profile.getMaxRetries())
            .build();
        return OpenAiChatModel.builder()
            .options(options)
            .build();
    }

    private EmbeddingModel buildEmbeddingModel(AiProfileNode profile) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
            .baseUrl(profile.getBaseUrl())
            .apiKey(profile.getApiKey() == null ? "" : profile.getApiKey())
            .model(profile.getEmbeddingModel())
            .dimensions(profile.getEmbeddingDimensions())
            .timeout(Duration.ofSeconds(profile.getTimeoutSeconds()))
            .maxRetries(profile.getMaxRetries())
            .build();
        return OpenAiEmbeddingModel.builder()
            .metadataMode(MetadataMode.EMBED)
            .options(options)
            .build();
    }

    private String cacheKey(AiProfileNode profile, String role) {
        return profile.getId() + ":" + profile.getRevision() + ":" + role;
    }
    @Override public void constructChat(String profileId) { chatModel(profileId); }
    @Override public void constructEmbedding(String profileId) { embeddingModel(profileId); }

}
