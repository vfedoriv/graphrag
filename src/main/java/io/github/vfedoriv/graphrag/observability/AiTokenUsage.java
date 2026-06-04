package io.github.vfedoriv.graphrag.observability;

import java.lang.reflect.Method;

public record AiTokenUsage(
    Long inputTokens,
    Long outputTokens,
    Long totalTokens
) {

    public static AiTokenUsage none() {
        return new AiTokenUsage(null, null, null);
    }

    public static AiTokenUsage fromResponse(Object response) {
        Object metadata = invoke(response, "getMetadata");
        Object usage = invoke(metadata, "getUsage");
        if (usage == null) {
            return none();
        }
        Long input = firstLong(usage, "getPromptTokens", "getInputTokens");
        Long output = firstLong(usage, "getCompletionTokens", "getOutputTokens");
        Long total = firstLong(usage, "getTotalTokens");
        return new AiTokenUsage(input, output, total);
    }

    public boolean hasAny() {
        return inputTokens != null || outputTokens != null || totalTokens != null;
    }

    private static Long firstLong(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            Long value = toLong(invoke(target, methodName));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
