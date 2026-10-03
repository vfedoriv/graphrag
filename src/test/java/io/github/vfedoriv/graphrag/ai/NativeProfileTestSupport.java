package io.github.vfedoriv.graphrag.ai;

import io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode;
import io.github.vfedoriv.graphrag.ai.models.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.ai.models.ResolvedChatBinding;
import io.github.vfedoriv.graphrag.ai.models.EmbeddingClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import static org.assertj.core.api.Assertions.assertThat;

/** Shared native preference with a plain model handle for unaffected workflow tests. */
public final class NativeProfileTestSupport {
    private NativeProfileTestSupport() { }
    public static ProfileScopedAiClientResolver resolver(ChatModel delegate) {
        ChatModel model = delegate == null ? null : prompt -> {
            if (prompt.getOptions() instanceof OpenAiChatOptions options)
                assertThat(options.getResponseFormat()).isNull();
            assertThat(prompt.getContents()).doesNotContain("graph-extraction-v1", "cypher-generation-v1");
            return delegate.call(prompt);
        };
        return new ProfileScopedAiClientResolver() {
            public EmbeddingClient embeddingClient() { return null; }
            public ChatModel chatModel() { return model; }
            public ResolvedChatBinding chatBinding() {
                return new ResolvedChatBinding(model, StructuredOutputMode.NATIVE_JSON_SCHEMA, "native-profile", 7L);
            }
        };
    }
}
