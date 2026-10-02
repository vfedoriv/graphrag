package io.github.vfedoriv.graphrag.search.query.adapters.model;

import io.github.vfedoriv.graphrag.search.query.domain.GeneratedCypher;
import io.github.vfedoriv.graphrag.search.query.ports.CypherGenerationClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiModelCallObservation;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiTokenUsage;
import io.github.vfedoriv.graphrag.service.AiProfileContext;
import io.github.vfedoriv.graphrag.service.AiRuntimeModelFactory;
import io.github.vfedoriv.graphrag.service.EmptyObjectProvider;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpringAiCypherGenerationClient implements CypherGenerationClient {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AiObservationService aiObservationService;
    private final ObjectProvider<AiRuntimeModelFactory> runtimeModelFactoryProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringAiCypherGenerationClient(
        ObjectProvider<ChatModel> chatModelProvider,
        AiObservationService aiObservationService,
        ObjectProvider<AiRuntimeModelFactory> runtimeModelFactoryProvider
    ) {
        this.chatModelProvider = chatModelProvider;
        this.aiObservationService = aiObservationService;
        this.runtimeModelFactoryProvider = runtimeModelFactoryProvider;
    }

    @Override
    public GeneratedCypher generate(SchemaDocument schema, String prompt, int maxRows) {
        long startNanos = System.nanoTime();
        String request = """
            You generate Cypher for Neo4j.
            Use ONLY labels, relationship types, and properties from this schema:
            %s

            Rules:
            - Return a read-only query only.
            - Use parameters for literal user values.
            - Include LIMIT %d if no smaller business-safe limit is required.
            - Return ONLY JSON with this shape:
              {"cypher":"...","explanation":"...","parameters":{}}

            User prompt:
            %s
            """.formatted(toJson(schema), maxRows, prompt);
        log.info(
            "Cypher generation model call started: schemaName={}, promptLength={}, requestLength={}, requestFingerprint={}",
            schema.name(),
            LogMetadata.length(prompt),
            request.length(),
            LogMetadata.fingerprint(request)
        );
        Map<String, String> attributes = new HashMap<>(aiObservationService.contentAttributes("ai.prompt", request));
        attributes.putAll(aiObservationService.langfuseInputAttributes(request));
        attributes.put("ai.user_prompt.length", String.valueOf(LogMetadata.length(prompt)));
        try (AiModelCallObservation observation = aiObservationService.startChatModelCall(
            AiObservationService.WORKFLOW_CYPHER_GENERATION,
            schema.name(),
            attributes
        )) {
            try {
                ChatModel chatModel = resolveChatModel();
                if (chatModel == null) {
                    throw new IllegalStateException("ChatModel bean is not available in application context");
                }
                log.info("Cypher generation resolved chatModelClass={}", chatModel.getClass().getName());
                org.springframework.ai.chat.model.ChatResponse chatResponse = chatModel.call(new Prompt(request));
                String content = chatResponse.getResult().getOutput().getText();
                log.info(
                    "Cypher generation model response received: responseLength={}, responseFingerprint={}",
                    LogMetadata.length(content),
                    LogMetadata.fingerprint(content)
                );
                observation.highCardinalityAttribute("ai.response.length", String.valueOf(LogMetadata.length(content)));
                observation.highCardinalityAttributes(aiObservationService.langfuseOutputAttributes(content));
                Payload payload = objectMapper.readValue(content, Payload.class);
                GeneratedCypher generated = new GeneratedCypher(
                    payload.cypher(),
                    payload.explanation(),
                    payload.parameters() == null ? Map.of() : payload.parameters()
                );
                observation.highCardinalityAttribute("ai.cypher.length", String.valueOf(LogMetadata.length(generated.cypher())));
                observation.highCardinalityAttribute("ai.cypher.parameter_count", String.valueOf(generated.parameters().size()));
                log.info(
                    "Cypher generation model call completed: cypherLength={}, parameterCount={}, elapsedMs={}",
                    LogMetadata.length(generated.cypher()),
                    generated.parameters().size(),
                    LogMetadata.elapsedMillis(startNanos)
                );
                observation.success(AiTokenUsage.fromResponse(chatResponse));
                return generated;
            } catch (Exception ex) {
                observation.error(ex);
                throw ex;
            }
        } catch (Exception ex) {
            log.error("Cypher generation model call failed: schemaName={}, exceptionType={}, messageFingerprint={}", schema.name(), LogMetadata.exceptionType(ex), LogMetadata.exceptionMessageFingerprint(ex));
            throw new IllegalArgumentException("Cypher generation response is invalid", ex);
        }
    }

    private ChatModel resolveChatModel() {
        String profileId = AiProfileContext.activeProfileId();
        AiRuntimeModelFactory factory = runtimeModelFactoryProvider.getIfAvailable();
        if (profileId != null && factory != null) {
            return factory.chatModel(profileId);
        }
        return chatModelProvider.getIfAvailable();
    }

    private String toJson(SchemaDocument schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception ex) {
            log.error("Failed to serialize schema for Cypher generation prompt: schemaName={}, exceptionType={}", schema.name(), LogMetadata.exceptionType(ex));
            return "{\"name\":\"unknown\",\"nodes\":[],\"relationships\":[]}";
        }
    }

    private record Payload(String cypher, String explanation, Map<String, Object> parameters) {
    }
}
