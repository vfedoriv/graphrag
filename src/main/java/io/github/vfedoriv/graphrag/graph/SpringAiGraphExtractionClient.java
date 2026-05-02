package io.github.vfedoriv.graphrag.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(ChatModel.class)
public class SpringAiGraphExtractionClient implements GraphExtractionClient {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringAiGraphExtractionClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public GraphExtractionResult extract(SchemaDocument schema, String chunkText) {
        String prompt = """
            You extract graph data from text.
            Use ONLY labels, relationship types, and properties from this schema:
            %s

            Return only JSON with this shape:
            {"nodes":[{"label":"...","properties":{},"confidence":0.0}],"relationships":[{"type":"...","fromLabel":"...","fromKey":{},"toLabel":"...","toKey":{},"properties":{},"confidence":0.0}]}

            Text chunk:
            %s
            """.formatted(schemaToCompactJson(schema), chunkText);
        try {
            String content = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
            return objectMapper.readValue(content, GraphExtractionResult.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Graph extraction response is invalid", e);
        }
    }

    private String schemaToCompactJson(SchemaDocument schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{\"name\":\"unknown\",\"nodes\":[],\"relationships\":[]}";
        }
    }
}
