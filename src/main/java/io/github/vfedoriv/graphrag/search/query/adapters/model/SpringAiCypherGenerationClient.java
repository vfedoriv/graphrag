package io.github.vfedoriv.graphrag.search.query.adapters.model;

import io.github.vfedoriv.graphrag.search.query.domain.GeneratedCypher;
import io.github.vfedoriv.graphrag.search.query.ports.CypherGenerationClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiModelCallObservation;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiTokenUsage;
import io.github.vfedoriv.graphrag.ai.models.ResolvedChatBinding;
import io.github.vfedoriv.graphrag.ai.models.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.ai.domain.StructuredOutputMode;
import io.github.vfedoriv.graphrag.ai.models.AiModelAccess;
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

    private final ProfileScopedAiClientResolver resolver;
    private final AiObservationService aiObservationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringAiCypherGenerationClient(
        ObjectProvider<ChatModel> chatModelProvider,
        AiObservationService aiObservationService,
        ObjectProvider<? extends AiModelAccess> runtimeModelFactoryProvider
    ) {
        this.resolver = ProfileScopedAiClientResolver.fromProviders(new io.github.vfedoriv.graphrag.ai.models.EmptyObjectProvider<>(), chatModelProvider, runtimeModelFactoryProvider);
        this.aiObservationService = aiObservationService;

    }

    @Override
    public GeneratedCypher generate(SchemaDocument schema, String prompt, int maxRows) {
        long startNanos = System.nanoTime();
        ResolvedChatBinding binding = resolver.chatBinding();
        boolean nativeMode = binding.mode() == StructuredOutputMode.NATIVE_JSON_SCHEMA;
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
        if (nativeMode) {
            request = request.replace("{\"cypher\":\"...\",\"explanation\":\"...\",\"parameters\":{}}", "{\"contractVersion\":\"cypher-generation-v1\",\"cypher\":\"...\",\"explanation\":\"...\",\"parameters\":[]}") + """

                Native entry encoding: encode every dynamically named map as an entry array
                [{"key":"name","value":...}]. Encode nested objects as {"entries":[...]};
                arrays preserve order; values may be strings, booleans, integers, numbers, or null.
                Use empty entry arrays for empty maps. Return only the native JSON object,
                including contractVersion, and all required fields. Never use fenced output.
                """;
        }
        log.info(
            "Cypher generation model call started: schemaName={}, promptLength={}, requestLength={}, requestFingerprint={}",
            schema.name(),
            LogMetadata.length(prompt),
            request.length(),
            LogMetadata.fingerprint(request)
        );
        Map<String, String> attributes = new HashMap<>(aiObservationService.contentAttributes("ai.prompt", request));
        attributes.putAll(aiObservationService.langfuseInputAttributes(request));
        attributes.put("ai.output.mode", binding.mode().name());
        attributes.put("ai.output.contract", nativeMode ? NativeCypherOutput.VERSION : "cypher-portable-v1");
        attributes.put("ai.user_prompt.length", String.valueOf(LogMetadata.length(prompt)));
        try (AiModelCallObservation observation = aiObservationService.startChatModelCall(
            AiObservationService.WORKFLOW_CYPHER_GENERATION,
            schema.name(),
            attributes
        )) {
            observation.lowCardinalityAttribute("ai.output.mode", binding.mode().name());
            observation.lowCardinalityAttribute("ai.output.contract", nativeMode ? NativeCypherOutput.VERSION : "cypher-portable-v1");
            try {
                ChatModel chatModel = binding.model();
                if (chatModel == null) {
                    if (nativeMode) throw new IllegalArgumentException("NATIVE_FORMAT_UNAVAILABLE");
                    throw new IllegalStateException("ChatModel bean is not available in application context");
                }
                log.info("Cypher generation resolved chatModelClass={}", chatModel.getClass().getName());
                org.springframework.ai.chat.model.ChatResponse chatResponse = chatModel.call(nativeMode ? NativeCypherCall.prompt(chatModel, request) : new Prompt(request));
                String content = nativeMode ? NativeCypherCall.content(chatResponse) : chatResponse.getResult().getOutput().getText();
                log.info(
                    "Cypher generation model response received: responseLength={}, responseFingerprint={}",
                    LogMetadata.length(content),
                    LogMetadata.fingerprint(content)
                );
                observation.highCardinalityAttribute("ai.response.length", String.valueOf(LogMetadata.length(content)));
                observation.highCardinalityAttributes(aiObservationService.langfuseOutputAttributes(content));
                GeneratedCypher generated;
                if (nativeMode) {
                    generated = new NativeCypherOutput().decode(content);
                } else {
                    Payload payload = objectMapper.readValue(content, Payload.class);
                    generated = new GeneratedCypher(payload.cypher(), payload.explanation(),
                        payload.parameters() == null ? Map.of() : payload.parameters());
                }
                observation.highCardinalityAttribute("ai.cypher.length", String.valueOf(LogMetadata.length(generated.cypher())));
                observation.highCardinalityAttribute("ai.cypher.parameter_count", String.valueOf(generated.parameters().size()));
                log.info(
                    "Cypher generation model call completed: cypherLength={}, parameterCount={}, elapsedMs={}",
                    LogMetadata.length(generated.cypher()),
                    generated.parameters().size(),
                    LogMetadata.elapsedMillis(startNanos)
                );
                observation.lowCardinalityAttribute("ai.output.outcome", "SUCCESS");
                observation.success(AiTokenUsage.fromResponse(chatResponse));
                return generated;
            } catch (Exception ex) {
                String category = nativeMode ? NativeCypherCall.category(ex) : AiObservationService.failureCategory(ex);
                observation.lowCardinalityAttribute("ai.output.outcome", category);
                observation.error(nativeMode ? new IllegalArgumentException(category) : ex,
                    category.equals("PROVIDER_FAILURE") ? AiObservationService.failureCategory(ex) : category);
                if (nativeMode) throw new IllegalArgumentException(category);
                throw ex;
            }
        } catch (Exception ex) {
            log.error("Cypher generation model call failed: schemaName={}, exceptionType={}, messageFingerprint={}", schema.name(), LogMetadata.exceptionType(ex), LogMetadata.exceptionMessageFingerprint(ex));
            throw new IllegalArgumentException("Cypher generation response is invalid", ex);
        }
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
