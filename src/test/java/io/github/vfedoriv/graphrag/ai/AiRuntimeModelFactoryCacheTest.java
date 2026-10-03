package io.github.vfedoriv.graphrag.ai;

import io.github.vfedoriv.graphrag.ai.adapters.provider.AiRuntimeModelFactory;
import io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService;
import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiRuntimeModelFactoryCacheTest {
    @Test
    void reusesActualProviderModelsUntilProfileRevisionChanges() {
        AiProfileService profiles = mock(AiProfileService.class);
        AiProfileNode profile = profile("profile");
        when(profiles.getNode("profile")).thenReturn(profile);
        AiRuntimeModelFactory models = new AiRuntimeModelFactory(profiles);
        ChatModel chat = models.chatModel("profile");
        EmbeddingModel embedding = models.embeddingModel("profile");
        assertThat(models.chatModel("profile")).isSameAs(chat);
        assertThat(models.embeddingModel("profile")).isSameAs(embedding);
        profile.setRevision(2);
        ChatModel revisedChat = models.chatModel("profile");
        EmbeddingModel revisedEmbedding = models.embeddingModel("profile");
        assertThat(revisedChat).isNotSameAs(chat);
        assertThat(revisedEmbedding).isNotSameAs(embedding);
        assertThat(models.chatModel("profile")).isSameAs(revisedChat);
        assertThat(models.embeddingModel("profile")).isSameAs(revisedEmbedding);
    }

    @Test
    void invalidationEvictsBothRolesAndPreservesNeighboringProfileId() {
        AiProfileService profiles = mock(AiProfileService.class);
        when(profiles.getNode("profile")).thenReturn(profile("profile"));
        when(profiles.getNode("profile-neighbor")).thenReturn(profile("profile-neighbor"));
        AiRuntimeModelFactory models = new AiRuntimeModelFactory(profiles);
        ChatModel chat = models.chatModel("profile");
        EmbeddingModel embedding = models.embeddingModel("profile");
        ChatModel neighborChat = models.chatModel("profile-neighbor");
        EmbeddingModel neighborEmbedding = models.embeddingModel("profile-neighbor");
        models.invalidate("profile");
        assertThat(models.chatModel("profile")).isNotSameAs(chat);
        assertThat(models.embeddingModel("profile")).isNotSameAs(embedding);
        assertThat(models.chatModel("profile-neighbor")).isSameAs(neighborChat);
        assertThat(models.embeddingModel("profile-neighbor")).isSameAs(neighborEmbedding);
    }

    private AiProfileNode profile(String id) {
        AiProfileNode profile = new AiProfileNode();
        profile.setId(id);
        profile.setRevision(1);
        profile.setBaseUrl("https://provider.invalid/v1");
        profile.setApiKey("unused-test-key");
        profile.setChatModel("chat-test");
        profile.setEmbeddingModel("text-embedding-3-small");
        profile.setEmbeddingDimensions(768);
        profile.setTimeoutSeconds(60);
        profile.setMaxRetries(2);
        return profile;
    }
}
