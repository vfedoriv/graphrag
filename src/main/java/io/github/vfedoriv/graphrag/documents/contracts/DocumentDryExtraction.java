package io.github.vfedoriv.graphrag.documents.contracts;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-chunk extraction and validation with no document-processing persistence effects. */
public interface DocumentDryExtraction {
    boolean available();
    Observation extract(SchemaDocument schema, String chunk, String aiProfileId);

    record Observation(Result raw, Result validated) { }
    record Result(List<Node> nodes, List<Relationship> relationships) {
        public Result {
            nodes = List.copyOf(nodes);
            relationships = List.copyOf(relationships);
        }
    }
    record Node(String label, Map<String, Object> properties, Double confidence) {
        public Node { properties = freezeMap(properties); }
    }
    record Relationship(String type, String fromLabel, Map<String, Object> fromKey,
                        String toLabel, Map<String, Object> toKey, Map<String, Object> properties, Double confidence) {
        public Relationship {
            fromKey = freezeMap(fromKey);
            toKey = freezeMap(toKey);
            properties = freezeMap(properties);
        }
    }

    private static Map<String, Object> freezeMap(Map<String, Object> source) {
        if (source == null) return null;
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, freeze(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, child) -> copy.put(key, freeze(child)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(child -> copy.add(freeze(child)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
