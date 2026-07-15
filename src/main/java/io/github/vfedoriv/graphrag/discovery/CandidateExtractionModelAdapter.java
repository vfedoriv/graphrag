package io.github.vfedoriv.graphrag.discovery;

import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiModelCallObservation;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiTokenUsage;
import java.util.HashMap;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
public class CandidateExtractionModelAdapter {

    private final ProfileScopedAiClientResolver clientResolver;
    private final AiObservationService observationService;

    public CandidateExtractionModelAdapter(
        ProfileScopedAiClientResolver clientResolver, AiObservationService observationService
    ) {
        this.clientResolver = clientResolver;
        this.observationService = observationService;
    }

    public CandidateExtractionResult extract(String portablePrompt) {
        ChatModel model = requireModel();
        BeanOutputConverter<CandidateExtractionResult> converter = new BeanOutputConverter<>(CandidateExtractionResult.class);
        Prompt prompt = portablePrompt(model, portablePrompt);
        Map<String, String> attributes = new HashMap<>(observationService.contentAttributes("ai.prompt", prompt.getContents()));
        attributes.put("ai.structured_output.mode", "portable");
        attributes.putAll(observationService.langfuseInputAttributes(prompt.getContents()));
        try (AiModelCallObservation observation = observationService.startChatModelCall(
            AiObservationService.WORKFLOW_SCHEMA_DISCOVERY, null, attributes
        )) {
            try {
                ChatResponse response = model.call(prompt);
                String responseText = responseText(response);
                observation.highCardinalityAttribute("ai.response.length", String.valueOf(LogMetadata.length(responseText)));
                observation.highCardinalityAttributes(observationService.langfuseOutputAttributes(responseText));
                CandidateExtractionResult result = converter.convert(responseText);
                if (result == null) {
                    throw new IllegalArgumentException("Candidate model response converted to null");
                }
                observation.success(AiTokenUsage.fromResponse(response));
                return result;
            } catch (RuntimeException exception) {
                observation.error(exception);
                throw exception;
            }
        }
    }

    private ChatModel requireModel() {
        ChatModel model = clientResolver.chatModel();
        if (model == null) {
            throw new IllegalStateException("No Spring AI ChatModel bean is configured");
        }
        return model;
    }

    Prompt portablePrompt(ChatModel model, String content) {
        return new Prompt(content);
    }

    private String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            throw new IllegalArgumentException("Candidate model response is missing a result");
        }
        AssistantMessage message = response.getResult().getOutput();
        if (message == null) {
            throw new IllegalArgumentException("Candidate model response is missing an assistant message");
        }
        String content = message.getText() == null ? "" : message.getText().trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("Candidate model response has blank normal assistant content");
        }
        return content;
    }
}
