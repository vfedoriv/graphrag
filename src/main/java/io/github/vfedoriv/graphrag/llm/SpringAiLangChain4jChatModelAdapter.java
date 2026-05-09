package io.github.vfedoriv.graphrag.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.springframework.ai.chat.prompt.Prompt;

public final class SpringAiLangChain4jChatModelAdapter implements ChatModel {

    private final org.springframework.ai.chat.model.ChatModel springChatModel;

    public SpringAiLangChain4jChatModelAdapter(org.springframework.ai.chat.model.ChatModel springChatModel) {
        this.springChatModel = springChatModel;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        String promptText = toPrompt(chatRequest.messages());
        String outputText = springChatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
        return ChatResponse.builder()
            .aiMessage(AiMessage.from(outputText))
            .build();
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
