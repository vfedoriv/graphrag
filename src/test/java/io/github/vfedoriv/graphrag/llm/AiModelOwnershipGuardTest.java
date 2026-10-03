package io.github.vfedoriv.graphrag.llm;

import io.github.vfedoriv.graphrag.bootstrap.AiModelOwnershipGuard;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.mock.env.MockEnvironment;

class AiModelOwnershipGuardTest {

    @Test
    void ignoresBeanCountsWhenNoAiProfileIsActive() {
        AiModelOwnershipGuard guard = new AiModelOwnershipGuard(
            environment("default"),
            Map.of(),
            Map.of()
        );

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    @Test
    void acceptsExactlyOneChatAndEmbeddingModelForOpenAiProfile() {
        AiModelOwnershipGuard guard = new AiModelOwnershipGuard(
            environment("openai"),
            Map.of("chatModel", Mockito.mock(ChatModel.class)),
            Map.of("embeddingModel", Mockito.mock(EmbeddingModel.class))
        );

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingChatModelForAiProfile() {
        AiModelOwnershipGuard guard = new AiModelOwnershipGuard(
            environment("lm_studio"),
            Map.of(),
            Map.of("embeddingModel", Mockito.mock(EmbeddingModel.class))
        );

        assertThatThrownBy(() -> guard.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Spring AI ChatModel ownership must resolve to exactly one Spring AI bean")
            .hasMessageContaining("found 0");
    }

    @Test
    void rejectsMultipleEmbeddingModelsForAiProfile() {
        AiModelOwnershipGuard guard = new AiModelOwnershipGuard(
            environment("openai"),
            Map.of("chatModel", Mockito.mock(ChatModel.class)),
            Map.of(
                "firstEmbeddingModel", Mockito.mock(EmbeddingModel.class),
                "secondEmbeddingModel", Mockito.mock(EmbeddingModel.class)
            )
        );

        assertThatThrownBy(() -> guard.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Spring AI EmbeddingModel ownership must resolve to exactly one Spring AI bean")
            .hasMessageContaining("found 2");
    }

    private MockEnvironment environment(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
