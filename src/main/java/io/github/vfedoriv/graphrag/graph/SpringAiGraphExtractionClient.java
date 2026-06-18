package io.github.vfedoriv.graphrag.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.observability.AiModelCallObservation;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiTokenUsage;
import io.github.vfedoriv.graphrag.service.AiProfileContext;
import io.github.vfedoriv.graphrag.service.AiRuntimeModelFactory;
import io.github.vfedoriv.graphrag.service.EmptyObjectProvider;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpringAiGraphExtractionClient implements GraphExtractionClient {
    private static final Set<String> NODE_FIELDS = Set.of("label", "properties", "confidence");
    private static final Set<String> RELATIONSHIP_FIELDS =
        Set.of("type", "fromLabel", "fromKey", "toLabel", "toKey", "properties", "confidence");
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AiObservationService aiObservationService;
    private final ObjectProvider<AiRuntimeModelFactory> runtimeModelFactoryProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper tolerantObjectMapper =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public SpringAiGraphExtractionClient(
        ObjectProvider<ChatModel> chatModelProvider,
        AiObservationService aiObservationService,
        ObjectProvider<AiRuntimeModelFactory> runtimeModelFactoryProvider
    ) {
        this.chatModelProvider = chatModelProvider;
        this.aiObservationService = aiObservationService;
        this.runtimeModelFactoryProvider = runtimeModelFactoryProvider;
    }

    @Override
    public GraphExtractionResult extract(SchemaDocument schema, String chunkText) {
        long startNanos = System.nanoTime();
        int chunkLength = chunkText == null ? 0 : chunkText.length();
        log.info("Graph extraction model call started: chunkLength={}", chunkLength);
        String prompt = """
            You extract graph data from text.
            Use ONLY labels, relationship types, and properties from this schema:
            %s

            Allowed relationship triples (type|fromLabel|toLabel):
            %s

            If no listed relationship triple applies, omit the relationship.

            Return only JSON with this shape:
            {"nodes":[{"label":"...","properties":{},"confidence":0.0}],"relationships":[{"type":"...","fromLabel":"...","fromKey":{},"toLabel":"...","toKey":{},"properties":{},"confidence":0.0}]}

            Text chunk:
            %s
            """.formatted(schemaToCompactJson(schema), allowedRelationshipTriples(schema), chunkText);
        log.info(
            "Graph extraction model request prepared: schemaName={}, promptLength={}, promptPreview={}",
            schema.name(),
            prompt.length(),
            LogSanitizer.preview(prompt)
        );
        if (log.isDebugEnabled()) {
            log.debug("Graph extraction model request: {}", prompt);
        }
        Map<String, String> attributes = new HashMap<>(aiObservationService.contentAttributes("ai.prompt", prompt));
        attributes.putAll(aiObservationService.langfuseInputAttributes(prompt));
        attributes.put("ai.chunk.length", String.valueOf(chunkLength));
        try (AiModelCallObservation observation = aiObservationService.startChatModelCall(
            AiObservationService.WORKFLOW_GRAPH_EXTRACTION,
            schema.name(),
            attributes
        )) {
            try {
                ChatModel chatModel = resolveChatModel();
                if (chatModel == null) {
                    throw new IllegalStateException("ChatModel bean is not available in application context");
                }
                log.info("Graph extraction resolved chatModelClass={}", chatModel.getClass().getName());
                org.springframework.ai.chat.model.ChatResponse chatResponse = chatModel.call(new Prompt(prompt));
                String content = chatResponse.getResult().getOutput().getText();
                log.info(
                    "Graph extraction model call completed: responseLength={}, responsePreview={}",
                    LogSanitizer.length(content),
                    LogSanitizer.preview(content)
                );
                if (log.isDebugEnabled()) {
                    log.debug("Graph extraction model response: {}", content);
                }
                observation.highCardinalityAttribute("ai.response.length", String.valueOf(LogSanitizer.length(content)));
                observation.highCardinalityAttributes(aiObservationService.langfuseOutputAttributes(content));
                String normalizedContent = extractJsonPayload(content);
                JsonNode responseJson = objectMapper.readTree(normalizedContent);
                logUnknownExtractionFields(schema, chunkLength, responseJson);
                GraphExtractionResult result = tolerantObjectMapper.treeToValue(responseJson, GraphExtractionResult.class);
                observation.highCardinalityAttribute("ai.graph.nodes", String.valueOf(result.nodes() == null ? 0 : result.nodes().size()));
                observation.highCardinalityAttribute(
                    "ai.graph.relationships",
                    String.valueOf(result.relationships() == null ? 0 : result.relationships().size())
                );
                log.info(
                    "Graph extraction model response parsed: nodes={}, relationships={}, elapsedMs={}",
                    result.nodes() == null ? 0 : result.nodes().size(),
                    result.relationships() == null ? 0 : result.relationships().size(),
                    LogSanitizer.elapsedMillis(startNanos)
                );
                observation.success(AiTokenUsage.fromResponse(chatResponse));
                return result;
            } catch (Exception e) {
                observation.error(e);
                throw e;
            }
        } catch (Exception e) {
            log.error(
                "Graph extraction model call failed: chunkLength={}, elapsedMs={}, message={}",
                chunkLength,
                LogSanitizer.elapsedMillis(startNanos),
                e.getMessage(),
                e
            );
            if (e instanceof RuntimeException runtimeException) {
                throw new IllegalArgumentException("Graph extraction response is invalid", runtimeException);
            }
            throw new IllegalArgumentException("Graph extraction response is invalid", e);
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
            log.error("Failed to serialize schema for graph extraction prompt: schemaName={}, message={}", schema.name(), e.getMessage(), e);
            return "{\"name\":\"unknown\",\"nodes\":[],\"relationships\":[]}";
        }
    }

    private String allowedRelationshipTriples(SchemaDocument schema) {
        if (schema.relationships() == null || schema.relationships().isEmpty()) {
            return "NONE";
        }
        return schema.relationships().stream()
            .map(rel -> rel.type() + "|" + rel.from() + "|" + rel.to())
            .collect(Collectors.joining(", "));
    }

    private void logUnknownExtractionFields(SchemaDocument schema, int chunkLength, JsonNode root) throws JsonProcessingException {
        if (root == null || !root.isObject()) {
            return;
        }
        Set<String> unknownNodeFields = collectUnknownFields(root.get("nodes"), NODE_FIELDS);
        Set<String> unknownRelationshipFields = collectUnknownFields(root.get("relationships"), RELATIONSHIP_FIELDS);
        if (unknownNodeFields.isEmpty() && unknownRelationshipFields.isEmpty()) {
            return;
        }
        log.warn(
            "Graph extraction response contains unknown fields that will be ignored: schemaName={}, chunkLength={}, unknownNodeFields={}, unknownRelationshipFields={}",
            schema.name(),
            chunkLength,
            unknownNodeFields,
            unknownRelationshipFields
        );
        if (log.isDebugEnabled()) {
            log.debug("Graph extraction response unknown field payload: {}", objectMapper.writeValueAsString(root));
        }
    }

    private Set<String> collectUnknownFields(JsonNode entries, Set<String> allowedFields) {
        Set<String> unknownFields = new HashSet<>();
        if (entries == null || !entries.isArray()) {
            return unknownFields;
        }
        for (JsonNode entry : entries) {
            if (!entry.isObject()) {
                continue;
            }
            entry.fieldNames().forEachRemaining(fieldName -> {
                if (!allowedFields.contains(fieldName)) {
                    unknownFields.add(fieldName);
                }
            });
        }
        return unknownFields;
    }
}
