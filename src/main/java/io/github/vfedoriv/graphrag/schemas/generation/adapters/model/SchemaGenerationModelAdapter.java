package io.github.vfedoriv.graphrag.schemas.generation.adapters.model;

import io.github.vfedoriv.graphrag.ai.models.ProfileScopedAiClientResolver;

import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.data.document.Document;
import io.github.vfedoriv.graphrag.schemas.generation.adapters.model.LLMGraphTransformerExt;
import io.github.vfedoriv.graphrag.schemas.generation.adapters.model.SpringAiLangChain4jChatModelAdapter;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiModelCallObservation;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiTokenUsage;
import io.github.vfedoriv.graphrag.ai.execution.AiProfileContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

@Slf4j
public final class SchemaGenerationModelAdapter {

    private final ProfileScopedAiClientResolver clientResolver;
    private final AiObservationService observationService;

    public SchemaGenerationModelAdapter(
        ProfileScopedAiClientResolver clientResolver,
        AiObservationService observationService
    ) {
        this.clientResolver = clientResolver;
        this.observationService = observationService;
    }

    public GraphDocument extractGraph(String text, String promptContract, String example) {
        LLMGraphTransformerExt transformer = new LLMGraphTransformerExt(
            new SpringAiLangChain4jChatModelAdapter(requireModel(), observationService),
            List.of(), List.of(), null, promptContract, example, 1
        );
        return transformer.transform(Document.from(text));
    }

    public String generateExample(String prompt) {
        Map<String, String> attributes = new HashMap<>(observationService.contentAttributes("ai.prompt", prompt));
        attributes.putAll(observationService.langfuseInputAttributes(prompt));
        try (AiModelCallObservation observation = observationService.startChatModelCall(
            AiObservationService.WORKFLOW_SCHEMA_GENERATION, null, attributes)) {
            try {
                ChatResponse response = requireModel().call(new Prompt(prompt));
                String text = responseText(response);
                observation.highCardinalityAttribute("ai.response.length", String.valueOf(LogMetadata.length(text)));
                observation.highCardinalityAttributes(observationService.langfuseOutputAttributes(text));
                observation.success(AiTokenUsage.fromResponse(response));
                return text;
            } catch (RuntimeException ex) {
                observation.error(ex);
                throw ex;
            }
        }
    }

    private ChatModel requireModel() {
        ChatModel model = clientResolver.chatModel();
        if (model == null) {
            throw new IllegalStateException("No Spring AI ChatModel bean is configured");
        }
        log.info("Schema generation resolved chatModelClass={} profileId={}",
            model.getClass().getName(), AiProfileContext.activeProfileId());
        return model;
    }

    private String responseText(ChatResponse response) {
        if (response.getResult() == null) {
            return "";
        }
        AssistantMessage message = response.getResult().getOutput();
        return message == null || message.getText() == null ? "" : message.getText().trim();
    }
}
