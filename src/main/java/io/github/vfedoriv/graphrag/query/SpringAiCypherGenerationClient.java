package io.github.vfedoriv.graphrag.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

@Component
public class SpringAiCypherGenerationClient implements CypherGenerationClient {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringAiCypherGenerationClient(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public GeneratedCypher generate(SchemaDocument schema, String prompt, int maxRows) {
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
        try {
            ChatModel chatModel = chatModelProvider.getIfAvailable();
            if (chatModel == null) {
                throw new IllegalStateException("ChatModel bean is not available in application context");
            }
            String content = chatModel.call(new Prompt(request)).getResult().getOutput().getText();
            Payload payload = objectMapper.readValue(content, Payload.class);
            return new GeneratedCypher(payload.cypher(), payload.explanation(), payload.parameters() == null ? Map.of() : payload.parameters());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cypher generation response is invalid", ex);
        }
    }

    private String toJson(SchemaDocument schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception ex) {
            return "{\"name\":\"unknown\",\"nodes\":[],\"relationships\":[]}";
        }
    }

    private record Payload(String cypher, String explanation, Map<String, Object> parameters) {
    }
}
