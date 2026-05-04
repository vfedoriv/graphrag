package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import dev.langchain4j.community.data.document.transformer.graph.GraphTransformer;
import dev.langchain4j.community.data.document.transformer.graph.LLMGraphTransformer;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class LangChain4jSchemaGenerationService implements SchemaGenerationService {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private final AppProperties appProperties;

    public LangChain4jSchemaGenerationService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public String generateYaml(String name, int version, String description, String text) {
        GraphTransformer transformer = LLMGraphTransformer.builder()
            .model(OpenAiChatModel.builder()
                .apiKey(appProperties.model().apiKey())
                .baseUrl(appProperties.model().baseUrl())
                .modelName(appProperties.model().chatModel())
                .build())
            .build();
        GraphDocument graphDocument = transformer.transform(Document.from(text));
        SchemaDocument schema = inferSchema(name, version, description, graphDocument);
        try {
            return YAML_MAPPER.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize generated schema to YAML", e);
        }
    }

    private SchemaDocument inferSchema(String name, int version, String description, GraphDocument graphDocument) {
        Set<SchemaDocument.NodeDefinition> nodes = new LinkedHashSet<>();
        Map<String, String> labelByNodeName = new LinkedHashMap<>();

        for (GraphNode node : graphDocument.nodes()) {
            String label = sanitizeLabel(node.type(), "Entity");
            String key = "id";
            nodes.add(new SchemaDocument.NodeDefinition(label, null, key, List.of()));
            labelByNodeName.put(node.id(), label);
        }

        Set<SchemaDocument.RelationshipDefinition> relationships = new LinkedHashSet<>();
        for (GraphEdge edge : graphDocument.relationships()) {
            String fromLabel = labelByNodeName.getOrDefault(edge.sourceNode().id(), "Entity");
            String toLabel = labelByNodeName.getOrDefault(edge.targetNode().id(), "Entity");
            String type = sanitizeRelation(edge.type());
            relationships.add(new SchemaDocument.RelationshipDefinition(type, fromLabel, toLabel, null, List.of()));
        }

        List<SchemaDocument.NodeDefinition> sortedNodes = new ArrayList<>(nodes);
        sortedNodes.sort(Comparator.comparing(SchemaDocument.NodeDefinition::label));

        List<SchemaDocument.RelationshipDefinition> sortedRelationships = new ArrayList<>(relationships);
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
}
