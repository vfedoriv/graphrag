package io.github.vfedoriv.graphrag.ai.execution;

import java.util.function.Supplier;

/** A captured execution scope; its provider state is private to AI. */
public interface CapturedAiExecution {
    <T> T call(Supplier<T> action);
}
