package io.github.vfedoriv.graphrag.graph;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
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
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
 *   <li>Property values must be scalar values; list-valued properties are rejected as invalid schema-generation output shape.</li>
 *   <li>All accepted scalar property values are converted to strings to fit GraphNode/GraphEdge map signature.</li>
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
        Think about useful domain properties that should exist on each node or relationship type, add these properties and assign concrete values for them from the text (when values available).
        If a useful properties is not directly stated but is important for the inferred schema or domain, add property and assign a representative value that matches the expected type.
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
        `head_properties`, `relation_properties`, and `tail_properties` MUST be JSON key/value maps with scalar string values.
        Do not use arrays as property values.
        Each node properties object (`head_properties` and `tail_properties`) MUST include:
        - `description`
        - `key` as comma-separated property names selected only from that same properties object
        - between 1 and 6 additional useful domain properties (excluding `description` and `key`)
        Required output shape:
        [
          {
            "head": "...",
            "head_type": "...",
            "head_properties": {"description": "...", "key": "usefulProperty1,usefulProperty3", "usefulProperty1": "...", "usefulProperty2": "...", "usefulPropertyN": "..."},
            "relation": "...",
            "relation_properties": {"description": "...", "usefulProperty1": "...", "usefulProperty2": "...", "usefulPropertyN": "..."},
            "tail": "...",
            "tail_type": "...",
            "tail_properties": {"description": "...", "key": "usefulProperty2,usefulProperty1", "usefulProperty1": "...", "usefulProperty2": "...", "usefulPropertyN": "..."}
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
        if (!isNullOrEmpty(prompt)) {
            return prompt;
        }

        boolean withAllowedNodes = !isNullOrEmpty(allowedNodes);
        boolean withAllowedRels = !isNullOrEmpty(allowedRelationships);

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
        if (isNullOrEmpty(parsedJson)) {
            return null;
        }

        Map<String, GraphNode> byCompositeKey = new LinkedHashMap<>();
        for (Map<String, Object> rel : parsedJson) {
            String head = asText(rel.get("head"));
            String tail = asText(rel.get("tail"));
            String relation = asText(rel.get("relation"));
            if (isNullOrBlank(head) || isNullOrBlank(tail) || isNullOrBlank(relation)) {
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
            if (isNullOrEmpty(relationProperties)) {
                relationProperties = extractProperties(rel.get("properties"));
            }
            relationships.add(GraphEdge.from(sourceNode, targetNode, relation, relationProperties));
        }

        if (isNullOrEmpty(nodes)) {
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
        String normalizedType = isNullOrBlank(type) ? DEFAULT_NODE_TYPE : type;
        String key = id + "|" + normalizedType;
        Map<String, String> mergedProperties = new HashMap<>();
        GraphNode existing = byCompositeKey.get(key);
        if (existing != null && existing.properties() != null) {
            mergedProperties.putAll(existing.properties());
        }
        Map<String, String> extracted = extractProperties(rawProperties);
        validateNodePropertyContract(normalizedType, extracted);
        mergedProperties.putAll(extracted);
        GraphNode mergedNode = GraphNode.from(id, normalizedType, mergedProperties);
        byCompositeKey.put(key, mergedNode);
        return mergedNode;
    }

    private List<Map<String, Object>> getJsonResult(List<ChatMessage> messages) {
        AtomicInteger attemptCounter = new AtomicInteger(0);
        return RetryUtils.withRetry(
            () -> {
                int attempt = attemptCounter.incrementAndGet();
                try {
                    ChatResponse chat = chatModel.chat(messages);
                    String rawText = chat.aiMessage().text();
                    log.info(
                        "LLM graph transformer raw response received: attempt={}, responseLength={}, responseFingerprint={}",
                        attempt,
                        LogMetadata.length(rawText),
                        LogMetadata.fingerprint(rawText)
                    );
                    String backtickText = getBacktickText(rawText);
                    List<Map<String, Object>> parsed = OBJECT_MAPPER.readValue(backtickText, new TypeReference<>() {});
                    log.info(
                        "LLM graph transformer parsed response: attempt={}, relationshipCount={}, nodeTypeCount={}, propertyKeyCount={}",
                        attempt,
                        parsed.size(),
                        nodeTypeCount(parsed),
                        propertyKeyCount(parsed)
                    );
                    return parsed;
                } catch (Exception e) {
                    log.error("LLM graph transformer failed to parse response: attempt={}, exceptionType={}", attempt, LogMetadata.exceptionType(e));
                    throw (e instanceof RuntimeException re) ? re : new IllegalStateException(e);
                }
            },
            maxAttempts
        );
    }

    private static long nodeTypeCount(List<Map<String, Object>> parsed) {
        return parsed.stream()
            .flatMap(rel -> java.util.stream.Stream.of(rel.get("head_type"), rel.get("tail_type")))
            .map(LLMGraphTransformerExt::asText)
            .filter(type -> !isNullOrBlank(type))
            .distinct()
            .count();
    }

    private static long propertyKeyCount(List<Map<String, Object>> parsed) {
        return parsed.stream()
            .flatMap(rel -> java.util.stream.Stream.of(
                rel.get("head_properties"),
                rel.get("relation_properties"),
                rel.get("tail_properties")))
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .flatMap(properties -> properties.keySet().stream())
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(key -> !isNullOrBlank(String.valueOf(key)))
            .distinct()
            .count();
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
            if (!(entry.getKey() instanceof String key) || isNullOrBlank(key)) {
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
            throw new IllegalArgumentException("Array-valued properties are not supported in schema generation output");
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
            } catch (Exception e) {
                log.error("Failed to serialize LLM graph transformer value to JSON: valueClass={}, exceptionType={}", value.getClass().getName(), LogMetadata.exceptionType(e));
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private static void validateNodePropertyContract(String nodeType, Map<String, String> properties) {
        String description = properties.get("description");
        String key = properties.get("key");
        if (isNullOrBlank(description)) {
            throw new IllegalArgumentException("Node properties must include non-empty 'description' for node type: " + nodeType);
        }
        if (isNullOrBlank(key)) {
            throw new IllegalArgumentException("Node properties must include non-empty 'key' for node type: " + nodeType);
        }
        List<String> keyParts = java.util.Arrays.stream(key.split(","))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .toList();
        if (keyParts.isEmpty()) {
            throw new IllegalArgumentException("Node properties 'key' must reference at least one property for node type: " + nodeType);
        }
        for (String keyPart : keyParts) {
            if (!properties.containsKey(keyPart)) {
                throw new IllegalArgumentException("Node key components must exist in node properties for node type: " + nodeType);
            }
        }
        Set<String> keyPartNames = new HashSet<>(keyParts);
        long usefulPropertyCount = properties.keySet().stream()
            .filter(propertyName -> !propertyName.equals("description") && !propertyName.equals("key"))
            .filter(propertyName -> !keyPartNames.contains(propertyName))
            .count();
        if (usefulPropertyCount == 0) {
            log.warn(
                "Node properties have no additional useful properties; adding placeholder marker: nodeType={}",
                nodeType
            );
            properties.put("additional_property_required", "SHOULD BE UPDATED");
            usefulPropertyCount = 1;
        }
        if (usefulPropertyCount > 6) {
            throw new IllegalArgumentException(
                "Node properties must include between 1 and 6 useful properties (excluding description/key) for node type: " + nodeType
            );
        }
    }

}
