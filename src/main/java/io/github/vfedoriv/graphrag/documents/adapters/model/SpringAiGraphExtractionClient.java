package io.github.vfedoriv.graphrag.documents.adapters.model;

import io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult;
import io.github.vfedoriv.graphrag.documents.ports.GraphExtractionClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.HashSet;
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
    private final ProfileScopedAiClientResolver resolver;
    private final AiObservationService aiObservationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper tolerantObjectMapper =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public SpringAiGraphExtractionClient(
        ObjectProvider<ChatModel> chatModelProvider,
        AiObservationService aiObservationService,
        ObjectProvider<? extends AiModelAccess> runtimeModelFactoryProvider
    ) {
        this.resolver = ProfileScopedAiClientResolver.fromProviders(new io.github.vfedoriv.graphrag.ai.models.EmptyObjectProvider<>(), chatModelProvider, runtimeModelFactoryProvider);
        this.aiObservationService = aiObservationService;

    }

    @Override
    public GraphExtractionResult extract(SchemaDocument schema, String chunkText) {
        long startNanos = System.nanoTime();
        ResolvedChatBinding binding = resolver.chatBinding();
        boolean nativeMode = binding.mode() == StructuredOutputMode.NATIVE_JSON_SCHEMA;
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
        if (nativeMode) {
            prompt = prompt.replace("{\"nodes\":[{\"label\":\"...\",\"properties\":{},\"confidence\":0.0}],\"relationships\":[{\"type\":\"...\",\"fromLabel\":\"...\",\"fromKey\":{},\"toLabel\":\"...\",\"toKey\":{},\"properties\":{},\"confidence\":0.0}]}", "{\"contractVersion\":\"graph-extraction-v1\",\"nodes\":[{\"label\":\"...\",\"properties\":[],\"confidence\":null}],\"relationships\":[{\"type\":\"...\",\"fromLabel\":\"...\",\"fromKey\":[],\"toLabel\":\"...\",\"toKey\":[],\"properties\":[],\"confidence\":null}]}") + """

                Native entry encoding: encode every dynamically named map as an entry array
                [{"key":"name","value":...}]. Encode nested objects as {"entries":[...]};
                arrays preserve order; values may be strings, booleans, integers, numbers, or null.
                Use empty entry arrays for empty maps. Return only the native JSON object,
                including contractVersion, and all required fields. Never use fenced output.
                """;
        }
        log.info(
            "Graph extraction model request prepared: schemaName={}, promptLength={}, promptFingerprint={}",
            schema.name(),
            prompt.length(),
            LogMetadata.fingerprint(prompt)
        );
        Map<String, String> attributes = new HashMap<>(aiObservationService.contentAttributes("ai.prompt", prompt));
        attributes.putAll(aiObservationService.langfuseInputAttributes(prompt));
        attributes.put("ai.output.mode", binding.mode().name());
        attributes.put("ai.output.contract", nativeMode ? NativeGraphOutput.VERSION : "graph-portable-v1");
        attributes.put("ai.chunk.length", String.valueOf(chunkLength));
        try (AiModelCallObservation observation = aiObservationService.startChatModelCall(
            AiObservationService.WORKFLOW_GRAPH_EXTRACTION,
            schema.name(),
            attributes
        )) {
            observation.lowCardinalityAttribute("ai.output.mode", binding.mode().name());
            observation.lowCardinalityAttribute("ai.output.contract", nativeMode ? NativeGraphOutput.VERSION : "graph-portable-v1");
            try {
                ChatModel chatModel = binding.model();
                if (chatModel == null) {
                    if (nativeMode) throw new IllegalArgumentException("NATIVE_FORMAT_UNAVAILABLE");
                    throw new IllegalStateException("ChatModel bean is not available in application context");
                }
                log.info("Graph extraction resolved chatModelClass={}", chatModel.getClass().getName());
                org.springframework.ai.chat.model.ChatResponse chatResponse = chatModel.call(nativeMode ? NativeGraphCall.prompt(chatModel, prompt) : new Prompt(prompt));
                String content = nativeMode ? NativeGraphCall.content(chatResponse) : chatResponse.getResult().getOutput().getText();
                log.info(
                    "Graph extraction model call completed: responseLength={}, responseFingerprint={}",
                    LogMetadata.length(content),
                    LogMetadata.fingerprint(content)
                );
                observation.highCardinalityAttribute("ai.response.length", String.valueOf(LogMetadata.length(content)));
                observation.highCardinalityAttributes(aiObservationService.langfuseOutputAttributes(content));
                GraphExtractionResult result;
                if (nativeMode) {
                    result = new NativeGraphOutput().decode(content);
                } else {
                    String normalizedContent = extractJsonPayload(content);
                    JsonNode responseJson = objectMapper.readTree(normalizedContent);
                    logUnknownExtractionFields(schema, chunkLength, responseJson);
                    result = tolerantObjectMapper.treeToValue(responseJson, GraphExtractionResult.class);
                }
                observation.highCardinalityAttribute("ai.graph.nodes", String.valueOf(result.nodes() == null ? 0 : result.nodes().size()));
                observation.highCardinalityAttribute(
                    "ai.graph.relationships",
                    String.valueOf(result.relationships() == null ? 0 : result.relationships().size())
                );
                log.info(
                    "Graph extraction model response parsed: nodes={}, relationships={}, elapsedMs={}",
                    result.nodes() == null ? 0 : result.nodes().size(),
                    result.relationships() == null ? 0 : result.relationships().size(),
                    LogMetadata.elapsedMillis(startNanos)
                );
                observation.lowCardinalityAttribute("ai.output.outcome", "SUCCESS");
                observation.success(AiTokenUsage.fromResponse(chatResponse));
                return result;
            } catch (Exception e) {
                String category = nativeMode ? NativeGraphCall.category(e) : AiObservationService.failureCategory(e);
                observation.lowCardinalityAttribute("ai.output.outcome", category);
                observation.error(nativeMode ? new IllegalArgumentException(category) : e,
                    category.equals("PROVIDER_FAILURE") ? AiObservationService.failureCategory(e) : category);
                if (nativeMode) throw new IllegalArgumentException(category);
                throw e;
            }
        } catch (Exception e) {
            log.error(
                "Graph extraction model call failed: chunkLength={}, elapsedMs={}, exceptionType={}",
                chunkLength,
                LogMetadata.elapsedMillis(startNanos),
                LogMetadata.exceptionType(e)
            );
            if (e instanceof RuntimeException runtimeException) {
                throw new IllegalArgumentException("Graph extraction response is invalid", runtimeException);
            }
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
            log.error("Failed to serialize schema for graph extraction prompt: schemaName={}, exceptionType={}", schema.name(), LogMetadata.exceptionType(e));
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
            "Graph extraction response contains unknown fields that will be ignored: schemaName={}, chunkLength={}, unknownNodeFieldCount={}, unknownRelationshipFieldCount={}",
            schema.name(),
            chunkLength,
            unknownNodeFields.size(),
            unknownRelationshipFields.size()
        );
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
