package io.github.vfedoriv.graphrag.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringAiCypherGenerationClient(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
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
            "Cypher generation model call started: schemaName={}, promptLength={}, requestLength={}, requestPreview={}",
            schema.name(),
            LogSanitizer.length(prompt),
            request.length(),
            LogSanitizer.preview(request)
        );
        if (log.isDebugEnabled()) {
            log.debug("Cypher generation model request: {}", request);
        }
        try {
            ChatModel chatModel = chatModelProvider.getIfAvailable();
            if (chatModel == null) {
                throw new IllegalStateException("ChatModel bean is not available in application context");
            }
            log.info("Cypher generation resolved chatModelClass={}", chatModel.getClass().getName());
            String content = chatModel.call(new Prompt(request)).getResult().getOutput().getText();
            log.info(
                "Cypher generation model response received: responseLength={}, responsePreview={}",
                LogSanitizer.length(content),
                LogSanitizer.preview(content)
            );
            if (log.isDebugEnabled()) {
                log.debug("Cypher generation model response: {}", content);
            }
            Payload payload = objectMapper.readValue(content, Payload.class);
            GeneratedCypher generated = new GeneratedCypher(payload.cypher(), payload.explanation(), payload.parameters() == null ? Map.of() : payload.parameters());
            log.info(
                "Cypher generation model call completed: cypherLength={}, parameterCount={}, elapsedMs={}",
                LogSanitizer.length(generated.cypher()),
                generated.parameters().size(),
                LogSanitizer.elapsedMillis(startNanos)
            );
            return generated;
        } catch (Exception ex) {
            log.error("Cypher generation model call failed: schemaName={}, message={}", schema.name(), ex.getMessage(), ex);
            throw new IllegalArgumentException("Cypher generation response is invalid", ex);
        }
    }

    private String toJson(SchemaDocument schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception ex) {
            log.error("Failed to serialize schema for Cypher generation prompt: schemaName={}, message={}", schema.name(), ex.getMessage(), ex);
            return "{\"name\":\"unknown\",\"nodes\":[],\"relationships\":[]}";
        }
    }

    private record Payload(String cypher, String explanation, Map<String, Object> parameters) {
    }
}
