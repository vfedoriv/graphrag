package io.github.vfedoriv.graphrag.ai.execution;

/** Captures the current profile model before asynchronous execution. */
public interface AiExecution {
    CapturedAiExecution captureChat(String profileId);
}
