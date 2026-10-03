package io.github.vfedoriv.graphrag.schemas.generation.adapters.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.github.vfedoriv.graphrag.observability.AiModelCallObservation;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiTokenUsage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;

public final class SpringAiLangChain4jChatModelAdapter implements ChatModel {

    private final org.springframework.ai.chat.model.ChatModel springChatModel;
    private final AiObservationService aiObservationService;

    public SpringAiLangChain4jChatModelAdapter(
        org.springframework.ai.chat.model.ChatModel springChatModel,
        AiObservationService aiObservationService
    ) {
        this.springChatModel = springChatModel;
        this.aiObservationService = aiObservationService;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        String promptText = toPrompt(chatRequest.messages());
        Map<String, String> attributes = new HashMap<>(aiObservationService.contentAttributes("ai.prompt", promptText));
        attributes.putAll(aiObservationService.langfuseInputAttributes(promptText));
        try (AiModelCallObservation observation = aiObservationService.startChatModelCall(
            AiObservationService.WORKFLOW_SCHEMA_GENERATION,
            null,
            attributes
        )) {
            try {
                org.springframework.ai.chat.model.ChatResponse springResponse = springChatModel.call(new Prompt(promptText));
                String outputText = springResponse.getResult().getOutput().getText();
                observation.highCardinalityAttribute("ai.response.length", String.valueOf(outputText == null ? 0 : outputText.length()));
                observation.highCardinalityAttributes(aiObservationService.langfuseOutputAttributes(outputText));
                observation.success(AiTokenUsage.fromResponse(springResponse));
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from(outputText))
                    .build();
            } catch (RuntimeException ex) {
                observation.error(ex);
                throw ex;
            }
        }
    }

    private String toPrompt(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage systemMessage) {
                builder.append("System:\n").append(systemMessage.text()).append("\n\n");
            } else if (message instanceof UserMessage userMessage && userMessage.hasSingleText()) {
                builder.append("User:\n").append(userMessage.singleText()).append("\n\n");
            }
        }
        return builder.toString();
    }
}
