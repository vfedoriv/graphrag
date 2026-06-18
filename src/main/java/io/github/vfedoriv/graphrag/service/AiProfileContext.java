package io.github.vfedoriv.graphrag.service;

import java.util.function.Supplier;

public final class AiProfileContext {

    private static final ThreadLocal<String> ACTIVE_PROFILE_ID = new ThreadLocal<>();

    private AiProfileContext() {
    }

    public static String activeProfileId() {
        return ACTIVE_PROFILE_ID.get();
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
}
