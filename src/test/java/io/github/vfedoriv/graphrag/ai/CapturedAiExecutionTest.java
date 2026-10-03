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
        AiModelAccess models = new AiModelAccess() {
            public io.github.vfedoriv.graphrag.ai.models.ResolvedChatBinding chatBinding(String profileId) {
                return new io.github.vfedoriv.graphrag.ai.models.ResolvedChatBinding(current.get(),
                    current.get() == original ? io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode.PORTABLE
                        : io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode.NATIVE_JSON_SCHEMA,
                    profileId, current.get() == original ? 1L : 2L);
            }
            public ChatModel chatModel(String profileId) { return current.get(); }
            public EmbeddingModel embeddingModel(String profileId) { throw new UnsupportedOperationException(); }
        };
        DefaultAiExecution execution = new DefaultAiExecution(models);
        io.github.vfedoriv.graphrag.ai.models.ProfileScopedAiClientResolver resolver =
            io.github.vfedoriv.graphrag.ai.models.ProfileScopedAiClientResolver.fromProviders(new io.github.vfedoriv.graphrag.ai.models.EmptyObjectProvider<>(),
                new org.springframework.beans.factory.ObjectProvider<ChatModel>() { public ChatModel getIfAvailable() { return replacement; } },
                new org.springframework.beans.factory.ObjectProvider<AiModelAccess>() { public AiModelAccess getIfAvailable() { return models; } });
        assertThat(resolver.chatBinding().mode()).isEqualTo(io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode.PORTABLE);
        CapturedAiExecution captured = execution.captureChat("captured");
        current.set(replacement);
        CapturedAiExecution nested = execution.captureChat("nested");
        assertThat(AiProfileContext.activeProfileId()).isNull();
        assertThat(AiModelContext.capturedChatModel()).isNull();
        String result = captured.call(() -> {
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("captured");
            assertThat(AiModelContext.capturedChatModel()).isSameAs(original);
            assertThat(resolver.chatBinding().model()).isSameAs(original);
            assertThat(resolver.chatBinding().profileRevision()).isEqualTo(1);
            assertThat(AiModelContext.capturedChatBinding().mode()).isEqualTo(io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode.PORTABLE);
            assertThatThrownBy(() -> nested.call(() -> {
                assertThat(AiProfileContext.activeProfileId()).isEqualTo("nested");
                assertThat(AiModelContext.capturedChatModel()).isSameAs(replacement);
                assertThat(resolver.chatBinding().mode()).isEqualTo(io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode.NATIVE_JSON_SCHEMA);
                assertThat(resolver.chatBinding().profileId()).isEqualTo("nested");
                assertThat(AiModelContext.capturedChatBinding().mode()).isEqualTo(io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode.NATIVE_JSON_SCHEMA);
                throw new IllegalStateException("provider failure");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(AiProfileContext.activeProfileId()).isEqualTo("captured");
            assertThat(AiModelContext.capturedChatModel()).isSameAs(original);
            assertThat(resolver.chatBinding().model()).isSameAs(original);
            assertThat(resolver.chatBinding().profileRevision()).isEqualTo(1);
            assertThat(AiModelContext.capturedChatBinding().mode()).isEqualTo(io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode.PORTABLE);
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
