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
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class LLMGraphTransformerExt extends LLMGraphTransformer {

    private static final String DEFAULT_NODE_TYPE = "Node";
    private static final Pattern BACKTICKS_PATTERN = Pattern.compile("```(.*?)```", Pattern.MULTILINE | Pattern.DOTALL);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final PromptTemplate SYSTEM_TEMPLATE = PromptTemplate.from(
        """
        You are a top-tier algorithm designed for extracting information in structured formats to build a knowledge graph schema.
        Your task is to identify entities and relations from a given text and generate output in JSON format.
        Each object must have keys: 'head', 'head_type', 'head_properties', 'relation', 'relation_properties', 'tail', 'tail_type', and 'tail_properties'.
        'head_properties', 'tail_properties', and 'relation_properties' must be JSON objects.
        Every node and relationship properties object must include a non-empty 'description' property with a concise schema-level description.
        Add useful domain properties that should exist on each node or relationship type, using concrete values from the text when available.
        If a useful property is not directly stated but is important for the inferred schema, include a representative value that matches the expected type.
        Use property names in lower camelCase.
        {{nodes}}
        {{rels}}
        IMPORTANT NOTES:
        - Return only a JSON array.
        - Don't add any explanation or extra text.
        {{additional}}
        """);
    private static final PromptTemplate USER_TEMPLATE = PromptTemplate.from(
        """
        Based on the following example, extract entities, relations, and useful schema properties from the provided text.
        {{nodes}}
        {{rels}}
        Required output shape:
        [
          {
            "head": "...",
            "head_type": "...",
            "head_properties": {"description": ["..."], "usefulProperty": ["..."]},
            "relation": "...",
            "relation_properties": {"description": ["..."], "usefulProperty": ["..."]},
            "tail": "...",
            "tail_type": "...",
            "tail_properties": {"description": ["..."], "usefulProperty": ["..."]}
          }
        ]

        Below are examples of text and their extracted entities and relationships.
        {{examples}}
        {{additional}}
        For the following text, extract entities and relations as in the provided example.
        Also infer useful node and relationship properties plus non-empty descriptions for schema generation.
        Text: {{input}}
        """);

    private final ChatModel chatModel;
    private final List<String> allowedNodes;
    private final List<String> allowedRelationships;
    private final List<ChatMessage> prompt;
    private final String additionalInstructions;
    private final String examples;
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
        this.allowedNodes = getOrDefault(allowedNodes, List.of());
        this.allowedRelationships = getOrDefault(allowedRelationships, List.of());
        this.prompt = prompt;
        this.additionalInstructions = getOrDefault(additionalInstructions, "");
        this.examples = ensureNotNull(examples, "examples");
        this.maxAttempts = getOrDefault(maxAttempts, 1);
    }

    @Override
    public List<ChatMessage> createUnstructuredPrompt(String text) {
        if (prompt != null && !prompt.isEmpty()) {
            return prompt;
        }

        boolean withAllowedNodes = allowedNodes != null && !allowedNodes.isEmpty();
        boolean withAllowedRels = allowedRelationships != null && !allowedRelationships.isEmpty();

        SystemMessage systemMessage = SYSTEM_TEMPLATE
            .apply(Map.of(
                "nodes", withAllowedNodes ? "The 'head_type' and 'tail_type' must be one of: " + allowedNodes : "",
                "rels", withAllowedRels ? "The 'relation' must be one of: " + allowedRelationships : "",
                "additional", additionalInstructions))
            .toSystemMessage();
        UserMessage userMessage = USER_TEMPLATE
            .apply(Map.of(
                "nodes", withAllowedNodes ? "# ENTITY TYPES:\n" + allowedNodes : "",
                "rels", withAllowedRels ? "# RELATION TYPES:\n" + allowedRelationships : "",
                "examples", examples,
                "additional", additionalInstructions,
                "input", text))
            .toUserMessage();

        return List.of(systemMessage, userMessage);
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
                log.info("LLM graph transformer raw response attempt {}: {}", attempt + 1, rawText);
                String backtickText = getBacktickText(rawText);
                List<Map<String, Object>> parsed = OBJECT_MAPPER.readValue(backtickText, new TypeReference<>() {});
                log.info("LLM graph transformer parsed response attempt {}: {}", attempt + 1, summarizeParsedResponse(parsed));
                return parsed;
            } catch (Exception e) {
                lastFailure = (e instanceof RuntimeException re) ? re : new IllegalStateException(e);
                log.warn("LLM graph transformer failed to parse response attempt {}", attempt + 1, e);
            }
        }
        throw lastFailure == null ? new IllegalStateException("Failed to parse graph transformer response") : lastFailure;
    }

    private static List<Map<String, Object>> summarizeParsedResponse(List<Map<String, Object>> parsed) {
        return parsed.stream()
            .map(rel -> Map.<String, Object>of(
                "head", asText(rel.get("head")),
                "headType", asText(rel.get("head_type")),
                "headProperties", propertySummary(rel.get("head_properties")),
                "relation", asText(rel.get("relation")),
                "relationProperties", propertySummary(rel.get("relation_properties")),
                "tail", asText(rel.get("tail")),
                "tailType", asText(rel.get("tail_type")),
                "tailProperties", propertySummary(rel.get("tail_properties"))))
            .toList();
    }

    private static Map<String, Object> propertySummary(@Nullable Object rawProperties) {
        Map<String, String> properties = extractProperties(rawProperties);
        return Map.of(
            "nonEmpty", !properties.isEmpty(),
            "hasDescription", !isBlank(properties.get("description")),
            "keys", properties.keySet());
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
