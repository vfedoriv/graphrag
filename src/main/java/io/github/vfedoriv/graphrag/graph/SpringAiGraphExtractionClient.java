package io.github.vfedoriv.graphrag.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

@Component
public class SpringAiGraphExtractionClient implements GraphExtractionClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiGraphExtractionClient.class);
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringAiGraphExtractionClient(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public GraphExtractionResult extract(SchemaDocument schema, String chunkText) {
        int chunkLength = chunkText == null ? 0 : chunkText.length();
        log.info("Graph extraction model call started: chunkLength={}", chunkLength);
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
            ChatModel chatModel = chatModelProvider.getIfAvailable();
            if (chatModel == null) {
                throw new IllegalStateException("ChatModel bean is not available in application context");
            }
            log.info("Graph extraction resolved chatModelClass={}", chatModel.getClass().getName());
            String content = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
            String normalizedContent = extractJsonPayload(content);
            String preview = content == null ? "" : content.substring(0, Math.min(300, content.length()));
            log.info(
                "Graph extraction model call completed: responseLength={}, responsePreview={}",
                content == null ? 0 : content.length(),
                preview
            );
            return objectMapper.readValue(normalizedContent, GraphExtractionResult.class);
        } catch (Exception e) {
            log.error("Graph extraction model call failed: chunkLength={}, message={}", chunkLength, e.getMessage(), e);
            throw new IllegalArgumentException("Graph extraction response is invalid", e);
        }
    }

    private String extractJsonPayload(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > -1) {
                text = text.substring(firstNewline + 1).trim();
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3).trim();
            }
        }
        int objectStart = text.indexOf('{');
        int objectEnd = text.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return text.substring(objectStart, objectEnd + 1);
        }
        int arrayStart = text.indexOf('[');
        int arrayEnd = text.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return text.substring(arrayStart, arrayEnd + 1);
        }
        return text;
    }

    private String schemaToCompactJson(SchemaDocument schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{\"name\":\"unknown\",\"nodes\":[],\"relationships\":[]}";
        }
    }
}
