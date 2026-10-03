package io.github.vfedoriv.graphrag.ai.adapters.provider;

import io.github.vfedoriv.graphrag.ai.execution.AiExecution;
import io.github.vfedoriv.graphrag.ai.execution.CapturedAiExecution;
import io.github.vfedoriv.graphrag.ai.models.AiModelAccess;
import java.util.function.Supplier;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiExecution implements AiExecution {
    private final AiModelAccess models;
    public DefaultAiExecution(AiModelAccess models) { this.models = models; }
    @Override public CapturedAiExecution captureChat(String profileId) {
        ChatModel captured = models.chatModel(profileId);
        return new CapturedAiExecution() {
            @Override public <T> T call(Supplier<T> action) {
                return AiModelContext.withCapturedChatModel(profileId, captured, action);
            }
        };
    }
}
