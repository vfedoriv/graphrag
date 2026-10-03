package io.github.vfedoriv.graphrag.ai;

import io.github.vfedoriv.graphrag.ai.adapters.provider.AiModelContext;
import io.github.vfedoriv.graphrag.ai.adapters.provider.DefaultAiExecution;
import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;
import io.github.vfedoriv.graphrag.ai.execution.CapturedAiExecution;
import io.github.vfedoriv.graphrag.ai.models.AiModelAccess;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CapturedAiExecutionTest {
    @Test
    void capturedModelSurvivesProviderChangeAndNestedFailureRestoresBothScopes() {
        ChatModel original = mock(ChatModel.class);
        ChatModel replacement = mock(ChatModel.class);
        AtomicReference<ChatModel> current = new AtomicReference<>(original);
        DefaultAiExecution execution = new DefaultAiExecution(new AiModelAccess() {
            public ChatModel chatModel(String profileId) { return current.get(); }
            public EmbeddingModel embeddingModel(String profileId) { throw new UnsupportedOperationException(); }
        });
        CapturedAiExecution captured = execution.captureChat("captured");
        current.set(replacement);
        CapturedAiExecution nested = execution.captureChat("nested");
        assertThat(AiProfileContext.activeProfileId()).isNull();
        assertThat(AiModelContext.capturedChatModel()).isNull();
        String result = captured.call(() -> {
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("captured");
            assertThat(AiModelContext.capturedChatModel()).isSameAs(original);
            assertThatThrownBy(() -> nested.call(() -> {
                assertThat(AiProfileContext.activeProfileId()).isEqualTo("nested");
                assertThat(AiModelContext.capturedChatModel()).isSameAs(replacement);
                throw new IllegalStateException("provider failure");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("captured");
            assertThat(AiModelContext.capturedChatModel()).isSameAs(original);
            return "result";
        });
        assertThat(result).isEqualTo("result");
        assertThat(AiProfileContext.activeProfileId()).isNull();
        assertThat(AiModelContext.capturedChatModel()).isNull();
        assertThatThrownBy(() -> captured.call(() -> { throw new IllegalStateException("failure"); }))
            .isInstanceOf(IllegalStateException.class);
        assertThat(AiProfileContext.activeProfileId()).isNull();
        assertThat(AiModelContext.capturedChatModel()).isNull();
    }
}
