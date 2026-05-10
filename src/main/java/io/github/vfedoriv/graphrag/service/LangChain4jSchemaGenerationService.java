package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import dev.langchain4j.data.document.Document;
import io.github.vfedoriv.graphrag.graph.LLMGraphTransformerExt;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.llm.SpringAiLangChain4jChatModelAdapter;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LangChain4jSchemaGenerationService implements SchemaGenerationService {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private final ObjectProvider<org.springframework.ai.chat.model.ChatModel> springChatModelProvider;

    public LangChain4jSchemaGenerationService(ObjectProvider<org.springframework.ai.chat.model.ChatModel> springChatModelProvider) {
        this.springChatModelProvider = springChatModelProvider;
    }

    @Override
    public String generateYaml(String name, int version, String description, String text, String example) {
        long startNanos = System.nanoTime();
        log.info(
            "Schema YAML generation started: name={}, version={}, descriptionPresent={}, textLength={}, exampleLength={}, textPreview={}",
            name,
            version,
            description != null && !description.isBlank(),
            LogSanitizer.length(text),
            LogSanitizer.length(example),
            LogSanitizer.preview(text)
        );
        if (log.isDebugEnabled()) {
            log.debug("Schema YAML generation source text: {}", text);
            log.debug("Schema YAML generation example: {}", example);
        }
        LLMGraphTransformerExt transformer = new LLMGraphTransformerExt(
            new SpringAiLangChain4jChatModelAdapter(requireSpringChatModel()),
            List.of(),
            List.of(),
            null,
            "",
            example,
            1
        );
        GraphDocument graphDocument = transformer.transform(Document.from(text));
        SchemaDocument schema = inferSchema(name, version, description, graphDocument);
        try {
            String yaml = YAML_MAPPER.writeValueAsString(schema);
            log.info(
                "Schema YAML generation completed: name={}, version={}, nodes={}, relationships={}, yamlLength={}, elapsedMs={}",
                name,
                version,
                schema.nodes().size(),
                schema.relationships().size(),
                yaml.length(),
                LogSanitizer.elapsedMillis(startNanos)
            );
            if (log.isDebugEnabled()) {
                log.debug("Generated schema YAML: {}", yaml);
            }
            return yaml;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize generated schema to YAML: name={}, version={}, message={}", name, version, e.getMessage(), e);
            throw new IllegalStateException("Failed to serialize generated schema to YAML", e);
        }
    }

    @Override
    public String generateExample(String text, String userPrompt) {
        long startNanos = System.nanoTime();
        String userInstruction = (userPrompt == null || userPrompt.isBlank()) ? "" : "\nAdditional guidance:\n" + userPrompt;
        String prompt = """
            You generate examples for graph extraction.
            Return only a JSON array where each object has keys 'head', 'head_type', 'relation', 'tail', and 'tail_type'.
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
        String response = requireSpringChatModel().call(new Prompt(prompt)).getResult().getOutput().getText();
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
            String label = sanitizeLabel(node.type(), "Entity");
            String key = "id";
            NodeAccumulator accumulator = nodes.computeIfAbsent(label, ignored -> new NodeAccumulator(label, key));
            accumulator.mergeFrom(node);
            labelByNodeName.put(node.id(), label);
        }

        Map<String, RelationshipAccumulator> relationships = new LinkedHashMap<>();
        for (GraphEdge edge : graphDocument.relationships()) {
            log.info("Processing edge {}", edge.toString());
            String fromLabel = labelByNodeName.getOrDefault(edge.sourceNode().id(), "Entity");
            String toLabel = labelByNodeName.getOrDefault(edge.targetNode().id(), "Entity");
            String type = sanitizeRelation(edge.type());
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

    private String sanitizeLabel(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isBlank()) {
            return fallback;
        }
        String first = cleaned.substring(0, 1).toUpperCase(Locale.ROOT);
        return first + cleaned.substring(1);
    }

    private String sanitizeRelation(String raw) {
        if (raw == null || raw.isBlank()) {
            return "RELATED_TO";
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "RELATED_TO" : cleaned;
    }

    private static String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return (right == null || right.isBlank()) ? null : right;
    }

    private static String inferPropertyType(String value) {
        if (value == null || value.isBlank()) {
            return "string";
        }
        String normalized = value.trim();
        if (normalized.matches("(?i)true|false")) {
            return "boolean";
        }
        if (normalized.matches("[+-]?\\d+")) {
            return "integer";
        }
        if (normalized.matches("[+-]?\\d*\\.\\d+")) {
            return "number";
        }
        if (normalized.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "date";
        }
        if (normalized.matches("\\d{4}-\\d{2}-\\d{2}[Tt ][0-2]\\d:[0-5]\\d:[0-5]\\d(?:\\.\\d{1,9})?(?:[Zz]|[+-][0-2]\\d:[0-5]\\d)?")) {
            return "datetime";
        }
        return "string";
    }

    private static final class NodeAccumulator {
        private final String label;
        private final String key;
        private String description;
        private final Map<String, SchemaDocument.PropertyDefinition> properties = new LinkedHashMap<>();

        private NodeAccumulator(String label, String key) {
            this.label = label;
            this.key = key;
        }

        private void mergeFrom(GraphNode node) {
            Map<String, String> rawProperties = node.properties() == null ? Map.of() : node.properties();
            description = firstNonBlank(description, rawProperties.get("description"));

            rawProperties.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> !entry.getKey().equalsIgnoreCase("description"))
                .forEach(entry -> properties.putIfAbsent(
                    entry.getKey(),
                    new SchemaDocument.PropertyDefinition(entry.getKey(), inferPropertyType(entry.getValue()), Boolean.FALSE)
                ));
        }

        private SchemaDocument.NodeDefinition toDefinition() {
            List<SchemaDocument.PropertyDefinition> sortedProperties = properties.values().stream()
                .sorted(Comparator.comparing(SchemaDocument.PropertyDefinition::name))
                .toList();
            return new SchemaDocument.NodeDefinition(label, description, key, sortedProperties);
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
            description = firstNonBlank(description, rawProperties.get("description"));

            rawProperties.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> !entry.getKey().equalsIgnoreCase("description"))
                .forEach(entry -> properties.putIfAbsent(
                    entry.getKey(),
                    new SchemaDocument.PropertyDefinition(entry.getKey(), inferPropertyType(entry.getValue()), Boolean.FALSE)
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

}
