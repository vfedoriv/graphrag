package io.github.vfedoriv.graphrag.ai.adapters.provider;

import io.github.vfedoriv.graphrag.ai.execution.AiExecution;
import io.github.vfedoriv.graphrag.ai.execution.CapturedAiExecution;
import io.github.vfedoriv.graphrag.ai.models.AiModelAccess;
import java.util.function.Supplier;
import io.github.vfedoriv.graphrag.ai.models.ResolvedChatBinding;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiExecution implements AiExecution {
    private final AiModelAccess models;
    public DefaultAiExecution(AiModelAccess models) { this.models = models; }
    @Override public CapturedAiExecution captureChat(String profileId) {
        ResolvedChatBinding captured = models.chatBinding(profileId);
        return new CapturedAiExecution() {
            @Override public <T> T call(Supplier<T> action) {
                return AiModelContext.withCapturedChatBinding(profileId, captured, action);
            }
        };
    }
}
