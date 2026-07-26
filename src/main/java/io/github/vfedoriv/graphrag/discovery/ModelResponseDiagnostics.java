package io.github.vfedoriv.graphrag.discovery;

import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiTokenUsage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

public record ModelResponseDiagnostics(
    String responseId,
    String model,
    String finishReason,
    AiTokenUsage tokenUsage,
    Integer normalContentLength,
    String normalContentFingerprint,
    boolean reasoningContentPresent,
    int reasoningContentLength
) {
    private static final String REASONING_CONTENT = "reasoning_content";
    private static final String REASONING_CONTENT_CAMEL = "reasoningContent";

    public static ModelResponseDiagnostics from(ChatResponse response) {
        ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
        Generation generation = response == null ? null : response.getResult();
        ChatGenerationMetadata generationMetadata = generation == null ? null : generation.getMetadata();
        AssistantMessage message = generation == null ? null : generation.getOutput();
        String normalContent = message == null || message.getText() == null ? null : message.getText().trim();
        Object reasoning = message == null ? null : firstReasoningValue(message);
        String reasoningText = reasoning == null ? null : String.valueOf(reasoning);
        return new ModelResponseDiagnostics(
            metadata == null ? null : metadata.getId(),
            metadata == null ? null : metadata.getModel(),
            generationMetadata == null ? null : generationMetadata.getFinishReason(),
            AiTokenUsage.fromResponse(response),
            normalContent == null ? null : normalContent.length(),
            normalContent == null ? null : LogMetadata.fingerprint(normalContent),
            reasoning != null,
            reasoningText == null ? 0 : reasoningText.length()
        );
    }

    public static ModelResponseDiagnostics none() {
        return new ModelResponseDiagnostics(null, null, null, AiTokenUsage.none(), null, null, false, 0);
    }

    public Map<String, String> observationAttributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        put(attributes, "ai.response.id", responseId);
        put(attributes, "ai.response.model", model);
        put(attributes, "ai.response.finish_reason", finishReason);
        put(attributes, "ai.response.normal_content.length", normalContentLength);
        put(attributes, "ai.response.normal_content.sha256", normalContentFingerprint);
        attributes.put("ai.response.reasoning.present", Boolean.toString(reasoningContentPresent));
        attributes.put("ai.response.reasoning.length", Integer.toString(reasoningContentLength));
        if (tokenUsage != null) {
            put(attributes, "ai.response.tokens.input", tokenUsage.inputTokens());
            put(attributes, "ai.response.tokens.output", tokenUsage.outputTokens());
            put(attributes, "ai.response.tokens.total", tokenUsage.totalTokens());
        }
        return Map.copyOf(attributes);
    }

    private static Object firstReasoningValue(AssistantMessage message) {
        Object value = message.getMetadata().get(REASONING_CONTENT);
        return value == null ? message.getMetadata().get(REASONING_CONTENT_CAMEL) : value;
    }

    private static void put(Map<String, String> attributes, String key, Object value) {
        if (value != null) {
            attributes.put(key, String.valueOf(value));
        }
    }
}
