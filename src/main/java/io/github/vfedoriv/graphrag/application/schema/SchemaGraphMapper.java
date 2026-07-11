package io.github.vfedoriv.graphrag.application.schema;

import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.service.SchemaGenerationNormalizationSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SchemaGraphMapper {

    public SchemaDocument map(String name, int version, String description, GraphDocument graphDocument) {
        Map<String, NodeAccumulator> nodes = new LinkedHashMap<>();
        Map<String, String> labelByNodeName = new LinkedHashMap<>();
        for (GraphNode node : graphDocument.nodes()) {
            String label = SchemaGenerationNormalizationSupport.sanitizeLabel(node.type(), "Entity");
            nodes.computeIfAbsent(label, ignored -> new NodeAccumulator(label)).mergeFrom(node);
            labelByNodeName.put(node.id(), label);
        }
        Map<String, RelationshipAccumulator> relationships = new LinkedHashMap<>();
        for (GraphEdge edge : graphDocument.relationships()) {
            String fromLabel = labelByNodeName.getOrDefault(edge.sourceNode().id(), "Entity");
            String toLabel = labelByNodeName.getOrDefault(edge.targetNode().id(), "Entity");
            String type = SchemaGenerationNormalizationSupport.sanitizeRelation(edge.type());
            String key = type + "|" + fromLabel + "|" + toLabel;
            relationships.computeIfAbsent(key, ignored -> new RelationshipAccumulator(type, fromLabel, toLabel)).mergeFrom(edge);
        }
        List<SchemaDocument.NodeDefinition> sortedNodes = nodes.values().stream()
            .map(NodeAccumulator::toDefinition).sorted(Comparator.comparing(SchemaDocument.NodeDefinition::label)).toList();
        List<SchemaDocument.RelationshipDefinition> sortedRelationships = relationships.values().stream()
            .map(RelationshipAccumulator::toDefinition)
            .sorted(Comparator.comparing(SchemaDocument.RelationshipDefinition::type)
                .thenComparing(SchemaDocument.RelationshipDefinition::from)
                .thenComparing(SchemaDocument.RelationshipDefinition::to))
            .toList();
        return new SchemaDocument(name, version, description, sortedNodes, sortedRelationships, List.of(), List.of());
    }

    private static final class NodeAccumulator {
        private final String label;
        private String description;
        private final List<String> keyCandidates = new ArrayList<>();
        private final Map<String, SchemaDocument.PropertyDefinition> properties = new LinkedHashMap<>();

        private NodeAccumulator(String label) {
            this.label = label;
        }

        private void mergeFrom(GraphNode node) {
            Map<String, String> raw = node.properties() == null ? Map.of() : node.properties();
            description = SchemaGenerationNormalizationSupport.firstNonBlank(description, raw.get("description"));
            if (raw.get("key") != null) {
                Arrays.stream(raw.get("key").split(",")).map(String::trim).filter(value -> !value.isBlank())
                    .filter(SchemaGenerationNormalizationSupport::isSafePropertyName)
                    .filter(value -> !keyCandidates.contains(value)).forEach(keyCandidates::add);
            }
            raw.entrySet().stream().filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> !entry.getKey().equalsIgnoreCase("description") && !entry.getKey().equalsIgnoreCase("key"))
                .forEach(entry -> properties.putIfAbsent(entry.getKey(), new SchemaDocument.PropertyDefinition(
                    entry.getKey(), SchemaGenerationNormalizationSupport.inferPropertyType(entry.getValue()), Boolean.FALSE)));
        }

        private SchemaDocument.NodeDefinition toDefinition() {
            List<SchemaDocument.PropertyDefinition> sorted = properties.values().stream()
                .sorted(Comparator.comparing(SchemaDocument.PropertyDefinition::name)).toList();
            List<String> names = sorted.stream().map(SchemaDocument.PropertyDefinition::name).filter(Objects::nonNull).toList();
            List<String> declared = keyCandidates.stream().filter(names::contains).toList();
            List<String> key = declared.isEmpty() ? SchemaGenerationNormalizationSupport.inferKeyCandidates(sorted) : declared;
            return new SchemaDocument.NodeDefinition(label, description, key, sorted);
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
            Map<String, String> raw = edge.properties() == null ? Map.of() : edge.properties();
            description = SchemaGenerationNormalizationSupport.firstNonBlank(description, raw.get("description"));
            raw.entrySet().stream().filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> !entry.getKey().equalsIgnoreCase("description"))
                .forEach(entry -> properties.putIfAbsent(entry.getKey(), new SchemaDocument.PropertyDefinition(
                    entry.getKey(), SchemaGenerationNormalizationSupport.inferPropertyType(entry.getValue()), Boolean.FALSE)));
        }

        private SchemaDocument.RelationshipDefinition toDefinition() {
            List<SchemaDocument.PropertyDefinition> sorted = properties.values().stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(SchemaDocument.PropertyDefinition::name)).toList();
            return new SchemaDocument.RelationshipDefinition(type, from, to, description, sorted);
        }
    }
}
