package io.github.vfedoriv.graphrag.ai.adapters.provider;

import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;
import java.util.function.Supplier;
import org.springframework.ai.chat.model.ChatModel;

/** AI-private SDK-bearing scope; business workflows only retain opaque execution handles. */
public final class AiModelContext {
    private static final ThreadLocal<ChatModel> CAPTURED_CHAT_MODEL = new ThreadLocal<>();
    private AiModelContext() { }
    public static ChatModel capturedChatModel() { return CAPTURED_CHAT_MODEL.get(); }
    public static <T> T withCapturedChatModel(String profileId, ChatModel chatModel, Supplier<T> supplier) {
        ChatModel previous = CAPTURED_CHAT_MODEL.get();
        CAPTURED_CHAT_MODEL.set(chatModel);
        try { return AiProfileContext.withProfile(profileId, supplier); }
        finally {
            if (previous == null) CAPTURED_CHAT_MODEL.remove();
            else CAPTURED_CHAT_MODEL.set(previous);
        }
    }
}
