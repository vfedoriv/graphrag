package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import dev.langchain4j.data.document.Document;
import io.github.vfedoriv.graphrag.dto.SchemaGenerationResult;
import io.github.vfedoriv.graphrag.dto.SchemaGenerationWarning;
import io.github.vfedoriv.graphrag.graph.LLMGraphTransformerExt;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.llm.SpringAiLangChain4jChatModelAdapter;
import io.github.vfedoriv.graphrag.schema.NodeKeySupport;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LangChain4jSchemaGenerationService implements SchemaGenerationService {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String SCHEMA_PROMPT_CONTRACT = """
        Schema key contract:
        - For each generated node, `key` can be either a single property name or a list of property names.
        - Every key component MUST exactly match a property name declared in that same node's properties.
        - In extraction output, include `key` inside head_properties/tail_properties as a comma-separated list
          of preferred key property names (e.g. "manufacturer,productName"), using only property names present in that node.
        - Avoid generic `id` unless `id` is explicitly present in that node's properties list.
        - Prefer meaningful canonical identity keys:
          Product: manufacturer + productName
          Model: manufacturer + modelCode
          SparePart: manufacturer + partNumber
          DocumentSection: documentId + sectionNumber or sectionTitle
          Location: country + state + city + street
          Person: fullName + birthDate
        - If multiple properties are needed for uniqueness, emit `key` as a list preserving deterministic order.
        """;
    private final ObjectProvider<org.springframework.ai.chat.model.ChatModel> springChatModelProvider;

    public LangChain4jSchemaGenerationService(ObjectProvider<org.springframework.ai.chat.model.ChatModel> springChatModelProvider) {
        this.springChatModelProvider = springChatModelProvider;
    }

    @Override
    public SchemaGenerationResult generate(String name, int version, String description, String text, String example) {
        long startNanos = System.nanoTime();
        log.info(
            "Schema JSON generation started: name={}, version={}, descriptionPresent={}, textLength={}, exampleLength={}, textPreview={}",
            name,
            version,
            description != null && !description.isBlank(),
            LogSanitizer.length(text),
            LogSanitizer.length(example),
            LogSanitizer.preview(text)
        );
        if (log.isDebugEnabled()) {
            log.debug("Schema JSON generation source text: {}", text);
            log.debug("Schema JSON generation example: {}", example);
        }
        LLMGraphTransformerExt transformer = new LLMGraphTransformerExt(
            new SpringAiLangChain4jChatModelAdapter(requireSpringChatModel()),
            List.of(),
            List.of(),
            null,
            SCHEMA_PROMPT_CONTRACT,
            example,
            1
        );
        GraphDocument graphDocument = transformer.transform(Document.from(text));
        SchemaDocument schema = inferSchema(name, version, description, graphDocument);
        List<SchemaGenerationWarning> warnings = buildKeyPropertyWarnings(schema);
        try {
            String json = JSON_MAPPER.writeValueAsString(schema);
            log.info(
                "Schema JSON generation completed: name={}, version={}, nodes={}, relationships={}, warningCount={}, jsonLength={}, elapsedMs={}",
                name,
                version,
                schema.nodes().size(),
                schema.relationships().size(),
                warnings.size(),
                json.length(),
                LogSanitizer.elapsedMillis(startNanos)
            );
            if (log.isDebugEnabled()) {
                log.debug("Generated schema JSON: {}", json);
            }
            return new SchemaGenerationResult(json, warnings);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize generated schema to JSON: name={}, version={}, message={}", name, version, e.getMessage(), e);
            throw new IllegalStateException("Failed to serialize generated schema to JSON", e);
        }
    }

    @Override
    public String generateExample(String text, String userPrompt) {
        long startNanos = System.nanoTime();
        String userInstruction = (userPrompt == null || userPrompt.isBlank()) ? "" : "\nAdditional guidance:\n" + userPrompt;
        String prompt = """
            You generate examples for graph extraction.
            Return only a JSON array where each object has keys 'head', 'head_type', 'relation', 'tail', and 'tail_type'.
            Do not wrap with any outer object (no {"example": ...}).
            No markdown and no explanations.

            Generate representative entity-relationship examples from this text so they can guide schema extraction.
            %s

            Text:
            %s
            """.formatted(userInstruction, text);
        log.info(
            "Schema example generation started: textLength={}, userPromptLength={}, promptPreview={}",
            LogSanitizer.length(text),
            LogSanitizer.length(userPrompt),
            LogSanitizer.preview(prompt)
        );
        if (log.isDebugEnabled()) {
            log.debug("Schema example generation prompt: {}", prompt);
        }
        ChatResponse chatResponse = requireSpringChatModel().call(new Prompt(prompt));
        AssistantMessage outputMessage = null;
        if (chatResponse.getResult() != null) {
            outputMessage = chatResponse.getResult().getOutput();
        }
        String response = "";
        if (outputMessage != null && outputMessage.getText() != null) {
            response = outputMessage.getText().trim();
        }
        log.info(
            "Schema example generation completed: responseLength={}, responsePreview={}, elapsedMs={}",
            LogSanitizer.length(response),
            LogSanitizer.preview(response),
            LogSanitizer.elapsedMillis(startNanos)
        );
        if (log.isDebugEnabled()) {
            log.debug("Schema example generation response: {}", response);
        }
        return response;
    }

    SchemaDocument inferSchema(String name, int version, String description, GraphDocument graphDocument) {
        Map<String, NodeAccumulator> nodes = new LinkedHashMap<>();
        Map<String, String> labelByNodeName = new LinkedHashMap<>();

        for (GraphNode node : graphDocument.nodes()) {
            log.info("Processing node {}", node.toString());
            String label = SchemaGenerationNormalizationSupport.sanitizeLabel(node.type(), "Entity");
            NodeAccumulator accumulator = nodes.computeIfAbsent(label, ignored -> new NodeAccumulator(label));
            accumulator.mergeFrom(node);
            labelByNodeName.put(node.id(), label);
        }

        Map<String, RelationshipAccumulator> relationships = new LinkedHashMap<>();
        for (GraphEdge edge : graphDocument.relationships()) {
            log.info("Processing edge {}", edge.toString());
            String fromLabel = labelByNodeName.getOrDefault(edge.sourceNode().id(), "Entity");
            String toLabel = labelByNodeName.getOrDefault(edge.targetNode().id(), "Entity");
            String type = SchemaGenerationNormalizationSupport.sanitizeRelation(edge.type());
            String key = type + "|" + fromLabel + "|" + toLabel;
            RelationshipAccumulator accumulator = relationships.computeIfAbsent(
                key, ignored -> new RelationshipAccumulator(type, fromLabel, toLabel));
            accumulator.mergeFrom(edge);
        }

        List<SchemaDocument.NodeDefinition> sortedNodes = new ArrayList<>(nodes.values().stream().map(NodeAccumulator::toDefinition).toList());
        sortedNodes.sort(Comparator.comparing(SchemaDocument.NodeDefinition::label));

        List<SchemaDocument.RelationshipDefinition> sortedRelationships = new ArrayList<>(
            relationships.values().stream().map(RelationshipAccumulator::toDefinition).toList());
        sortedRelationships.sort(Comparator
            .comparing(SchemaDocument.RelationshipDefinition::type)
            .thenComparing(SchemaDocument.RelationshipDefinition::from)
            .thenComparing(SchemaDocument.RelationshipDefinition::to));

        return new SchemaDocument(
            name,
            version,
            description,
            sortedNodes,
            sortedRelationships,
            List.of(),
            List.of()
        );
    }

    private static final class NodeAccumulator {
        private final String label;
        private String description;
        private final List<String> keyCandidates = new ArrayList<>();
        private final List<String> discardedKeyCandidates = new ArrayList<>();
        private final Map<String, SchemaDocument.PropertyDefinition> properties = new LinkedHashMap<>();

        private NodeAccumulator(String label) {
            this.label = label;
        }

        private void mergeFrom(GraphNode node) {
            Map<String, String> rawProperties = node.properties() == null ? Map.of() : node.properties();
            description = SchemaGenerationNormalizationSupport.firstNonBlank(description, rawProperties.get("description"));
            mergeKeyCandidates(rawProperties.get("key"));

            rawProperties.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> !entry.getKey().equalsIgnoreCase("description"))
                .filter(entry -> !entry.getKey().equalsIgnoreCase("key"))
                .forEach(entry -> properties.putIfAbsent(
                    entry.getKey(),
                    new SchemaDocument.PropertyDefinition(entry.getKey(), SchemaGenerationNormalizationSupport.inferPropertyType(entry.getValue()), Boolean.FALSE)
                ));
        }

        private SchemaDocument.NodeDefinition toDefinition() {
            List<SchemaDocument.PropertyDefinition> sortedProperties = properties.values().stream()
                .sorted(Comparator.comparing(SchemaDocument.PropertyDefinition::name))
                .toList();
            List<String> propertyNames = sortedProperties.stream()
                .map(SchemaDocument.PropertyDefinition::name)
                .filter(Objects::nonNull)
                .toList();
            List<String> declaredCandidates = keyCandidates.stream()
                .filter(propertyNames::contains)
                .toList();
            for (String keyCandidate : keyCandidates) {
                if (!propertyNames.contains(keyCandidate) && !discardedKeyCandidates.contains(keyCandidate)) {
                    discardedKeyCandidates.add(keyCandidate);
                }
            }
            List<String> key = declaredCandidates.isEmpty() ? SchemaGenerationNormalizationSupport.inferKeyCandidates(sortedProperties) : declaredCandidates;
            return new SchemaDocument.NodeDefinition(label, description, key, sortedProperties);
        }

        private void mergeKeyCandidates(String raw) {
            if (raw == null || raw.isBlank()) {
                return;
            }
            List<String> candidates = java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(SchemaGenerationNormalizationSupport::isSafePropertyName)
                .toList();
            for (String candidate : candidates) {
                if (!keyCandidates.contains(candidate)) {
                    keyCandidates.add(candidate);
                }
            }
        }

        private static boolean isSafePropertyName(String value) {
            return value.matches("[A-Za-z_][A-Za-z0-9_]*");
        }
    }

    private static final class RelationshipAccumulator {
        private final String type;
        private final String from;
        private final String to;
        private String description;
        private final Map<String, SchemaDocument.PropertyDefinition> properties = new LinkedHashMap<>();

        private RelationshipAccumulator(String type, String from, String to) {
            this.type = type;
            this.from = from;
            this.to = to;
        }

        private void mergeFrom(GraphEdge edge) {
            Map<String, String> rawProperties = edge.properties() == null ? Map.of() : edge.properties();
            description = SchemaGenerationNormalizationSupport.firstNonBlank(description, rawProperties.get("description"));

            rawProperties.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> !entry.getKey().equalsIgnoreCase("description"))
                .forEach(entry -> properties.putIfAbsent(
                    entry.getKey(),
                    new SchemaDocument.PropertyDefinition(entry.getKey(), SchemaGenerationNormalizationSupport.inferPropertyType(entry.getValue()), Boolean.FALSE)
                ));
        }

        private SchemaDocument.RelationshipDefinition toDefinition() {
            List<SchemaDocument.PropertyDefinition> sortedProperties = properties.values().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SchemaDocument.PropertyDefinition::name))
                .toList();
            return new SchemaDocument.RelationshipDefinition(type, from, to, description, sortedProperties);
        }
    }

    private org.springframework.ai.chat.model.ChatModel requireSpringChatModel() {
        org.springframework.ai.chat.model.ChatModel model = springChatModelProvider.getIfAvailable();
        if (model == null) {
            log.error("Spring AI ChatModel bean is missing for schema generation");
            throw new IllegalStateException("No Spring AI ChatModel bean is configured");
        }
        log.info("Schema generation resolved chatModelClass={}", model.getClass().getName());
        return model;
    }

    static List<SchemaGenerationWarning> buildKeyPropertyWarnings(SchemaDocument schema) {
        List<SchemaGenerationWarning> warnings = new ArrayList<>();
        List<SchemaDocument.NodeDefinition> nodes = schema == null ? List.of() : schema.nodes();
        if (nodes == null || nodes.isEmpty()) {
            return warnings;
        }
        for (int index = 0; index < nodes.size(); index++) {
            SchemaDocument.NodeDefinition node = nodes.get(index);
            List<String> keyNames = NodeKeySupport.normalizedKeys(node);
            if (node == null) {
                continue;
            }
            List<SchemaDocument.PropertyDefinition> properties = node.properties();
            List<String> propertyNames = properties == null ? List.of() : properties.stream()
                .filter(Objects::nonNull)
                .map(SchemaDocument.PropertyDefinition::name)
                .filter(Objects::nonNull)
                .toList();
            String nodeLabel = node.label() == null ? "(unknown)" : node.label();
            if (keyNames.isEmpty()) {
                warnings.add(new SchemaGenerationWarning(
                    index,
                    nodeLabel,
                    "NODE_KEY_MISSING",
                    "Node key is missing for node '%s'".formatted(nodeLabel),
                    List.of(
                        "Choose one or more existing properties as node key",
                        "Add canonical identity properties if current properties are not unique"
                    )
                ));
                continue;
            }
            for (String keyName : keyNames) {
                if (propertyNames.contains(keyName)) {
                    continue;
                }
                warnings.add(new SchemaGenerationWarning(
                    index,
                    nodeLabel,
                    "NODE_KEY_PROPERTY_MISMATCH",
                    "Node key component '%s' is not declared in properties for node '%s'".formatted(keyName, nodeLabel),
                    List.of(
                        "Add property '%s' to node properties".formatted(keyName),
                        "Change node key to use existing property names"
                    )
                ));
            }
        }
        return warnings;
    }

}
