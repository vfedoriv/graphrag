package io.github.vfedoriv.graphrag.service;

import java.util.function.Supplier;
import org.springframework.ai.chat.model.ChatModel;

public final class AiProfileContext {

    private static final ThreadLocal<String> ACTIVE_PROFILE_ID = new ThreadLocal<>();
    private static final ThreadLocal<ChatModel> CAPTURED_CHAT_MODEL = new ThreadLocal<>();

    private AiProfileContext() {
    }

    public static String activeProfileId() {
        return ACTIVE_PROFILE_ID.get();
    }

    public static ChatModel capturedChatModel() {
        return CAPTURED_CHAT_MODEL.get();
    }

    public static <T> T withProfile(String profileId, Supplier<T> supplier) {
        String previous = ACTIVE_PROFILE_ID.get();
        ACTIVE_PROFILE_ID.set(profileId);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                ACTIVE_PROFILE_ID.remove();
            } else {
                ACTIVE_PROFILE_ID.set(previous);
            }
        }
    }

    public static void withProfile(String profileId, Runnable runnable) {
        withProfile(profileId, () -> {
            runnable.run();
            return null;
        });
    }

    public static <T> T withCapturedChatModel(String profileId, ChatModel chatModel, Supplier<T> supplier) {
        ChatModel previous = CAPTURED_CHAT_MODEL.get();
        CAPTURED_CHAT_MODEL.set(chatModel);
        try {
            return withProfile(profileId, supplier);
        } finally {
            if (previous == null) {
                CAPTURED_CHAT_MODEL.remove();
            } else {
                CAPTURED_CHAT_MODEL.set(previous);
            }
        }
    }
}
