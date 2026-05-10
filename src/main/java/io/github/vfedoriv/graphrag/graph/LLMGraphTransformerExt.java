package io.github.vfedoriv.graphrag.graph;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import dev.langchain4j.community.data.document.transformer.graph.LLMGraphTransformer;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.lang.Nullable;

/**
 * Extension of LLMGraphTransformer that preserves node/edge properties.
 *
 * <p>Mapping assumptions aligned with the Python reference:
 * <ul>
 *   <li>Node properties may come from `head_properties` and `tail_properties` maps.</li>
 *   <li>Relationship properties may come from `relation_properties` (preferred) or `properties`.</li>
 *   <li>If a property value is a list: empty list is dropped, single item is flattened, multi-item is JSON-serialized.</li>
 *   <li>All property values are converted to strings to fit GraphNode/GraphEdge map signature.</li>
 * </ul>
 */
public class LLMGraphTransformerExt extends LLMGraphTransformer {

    private static final String DEFAULT_NODE_TYPE = "Node";
    private static final Pattern BACKTICKS_PATTERN = Pattern.compile("```(.*?)```", Pattern.MULTILINE | Pattern.DOTALL);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final Integer maxAttempts;

    public LLMGraphTransformerExt(
        ChatModel chatModel,
        List<String> allowedNodes,
        List<String> allowedRelationships,
        List<ChatMessage> prompt,
        String additionalInstructions,
        String examples,
        Integer maxAttempts
    ) {
        super(chatModel, allowedNodes, allowedRelationships, prompt, additionalInstructions, examples, maxAttempts);
        this.chatModel = ensureNotNull(chatModel, "chatModel");
        this.maxAttempts = getOrDefault(maxAttempts, 1);
    }

    @Override
    public GraphDocument transform(Document document) {
        List<ChatMessage> messages = createUnstructuredPrompt(document.text());

        Set<GraphNode> nodes = new HashSet<>();
        Set<GraphEdge> relationships = new HashSet<>();
        List<Map<String, Object>> parsedJson = getJsonResult(messages);
        if (parsedJson == null || parsedJson.isEmpty()) {
            return null;
        }

        Map<String, GraphNode> byCompositeKey = new LinkedHashMap<>();
        for (Map<String, Object> rel : parsedJson) {
            String head = asText(rel.get("head"));
            String tail = asText(rel.get("tail"));
            String relation = asText(rel.get("relation"));
            if (isBlank(head) || isBlank(tail) || isBlank(relation)) {
                continue;
            }

            GraphNode sourceNode = buildOrMergeNode(
                byCompositeKey,
                head,
                asText(rel.getOrDefault("head_type", DEFAULT_NODE_TYPE)),
                rel.get("head_properties")
            );
            GraphNode targetNode = buildOrMergeNode(
                byCompositeKey,
                tail,
                asText(rel.getOrDefault("tail_type", DEFAULT_NODE_TYPE)),
                rel.get("tail_properties")
            );
            nodes.add(sourceNode);
            nodes.add(targetNode);

            Map<String, String> relationProperties = extractProperties(rel.get("relation_properties"));
            if (relationProperties.isEmpty()) {
                relationProperties = extractProperties(rel.get("properties"));
            }
            relationships.add(GraphEdge.from(sourceNode, targetNode, relation, relationProperties));
        }

        if (nodes.isEmpty()) {
            return null;
        }
        return new GraphDocument(nodes, relationships, document);
    }

    private GraphNode buildOrMergeNode(
        Map<String, GraphNode> byCompositeKey,
        String id,
        String type,
        @Nullable Object rawProperties
    ) {
        String normalizedType = isBlank(type) ? DEFAULT_NODE_TYPE : type;
        String key = id + "|" + normalizedType;
        Map<String, String> mergedProperties = new HashMap<>();
        GraphNode existing = byCompositeKey.get(key);
        if (existing != null && existing.properties() != null) {
            mergedProperties.putAll(existing.properties());
        }
        mergedProperties.putAll(extractProperties(rawProperties));
        GraphNode mergedNode = GraphNode.from(id, normalizedType, mergedProperties);
        byCompositeKey.put(key, mergedNode);
        return mergedNode;
    }

    private List<Map<String, Object>> getJsonResult(List<ChatMessage> messages) {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                ChatResponse chat = chatModel.chat(messages);
                String rawText = chat.aiMessage().text();
                String backtickText = getBacktickText(rawText);
                return OBJECT_MAPPER.readValue(backtickText, new TypeReference<>() {});
            } catch (Exception e) {
                lastFailure = (e instanceof RuntimeException re) ? re : new IllegalStateException(e);
            }
        }
        throw lastFailure == null ? new IllegalStateException("Failed to parse graph transformer response") : lastFailure;
    }

    private static String getBacktickText(String text) {
        Matcher matcher = BACKTICKS_PATTERN.matcher(text == null ? "" : text);
        String extracted;
        if (matcher.find()) {
            extracted = matcher.group(1);
        }
        else {
            extracted = text;
        }
        if (extracted == null) {
            return "";
        }
        String trimmed = extracted.trim();
        // Handle fenced code blocks like ```json ... ```
        if (trimmed.startsWith("json")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd > -1) {
                return trimmed.substring(firstLineEnd + 1).trim();
            }
        }
        return trimmed;
    }

    private static Map<String, String> extractProperties(@Nullable Object rawProperties) {
        if (!(rawProperties instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                continue;
            }
            Object normalizedValue = normalizePropertyValue(entry.getValue());
            if (normalizedValue != null) {
                normalized.put(key, asText(normalizedValue));
            }
        }
        return normalized;
    }

    private static Object normalizePropertyValue(@Nullable Object value) {
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }
            if (list.size() == 1) {
                return list.getFirst();
            }
        }
        return value;
    }

    private static String asText(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String str) {
            return str;
        }
        if (value instanceof List<?> || value instanceof Map<?, ?>) {
            try {
                return OBJECT_MAPPER.writeValueAsString(value);
            } catch (Exception ignored) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
