package io.github.vfedoriv.graphrag.ai.adapters.provider;

import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;
import java.util.function.Supplier;
import org.springframework.ai.chat.model.ChatModel;
import io.github.vfedoriv.graphrag.ai.models.ResolvedChatBinding;

/** AI-private SDK-bearing scope; business workflows only retain opaque execution handles. */
public final class AiModelContext {
    private static final ThreadLocal<ResolvedChatBinding> CAPTURED_CHAT_MODEL = new ThreadLocal<>();
    private AiModelContext() { }
    public static ChatModel capturedChatModel() { return CAPTURED_CHAT_MODEL.get() == null ? null : CAPTURED_CHAT_MODEL.get().model(); }
    public static <T> T withCapturedChatModel(String profileId, ChatModel chatModel, Supplier<T> supplier) {
        return withCapturedChatBinding(profileId, ResolvedChatBinding.portable(chatModel), supplier);
    }
    public static ResolvedChatBinding capturedChatBinding() { return CAPTURED_CHAT_MODEL.get(); }
    public static <T> T withCapturedChatBinding(String profileId, ResolvedChatBinding binding, Supplier<T> supplier) {
        ResolvedChatBinding previous = CAPTURED_CHAT_MODEL.get();
        CAPTURED_CHAT_MODEL.set(binding);
        try { return AiProfileContext.withProfile(profileId, supplier); }
        finally {
            if (previous == null) CAPTURED_CHAT_MODEL.remove();
            else CAPTURED_CHAT_MODEL.set(previous);
        }
    }
}
