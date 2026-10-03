package io.github.vfedoriv.graphrag.ai.models;

import io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;

/** One revision's model and non-secret output preference. */
public record ResolvedChatBinding(ChatModel model, StructuredOutputMode mode, String profileId, Long profileRevision) {
    public ResolvedChatBinding { Objects.requireNonNull(mode); }
    public static ResolvedChatBinding portable(ChatModel model) {
        return new ResolvedChatBinding(model, StructuredOutputMode.PORTABLE, null, null);
    }
}
