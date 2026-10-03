package io.github.vfedoriv.graphrag.documents.adapters.model;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Private provider envelope; ordinary domain values leave this adapter. */
final class NativeGraphOutput {
    static final String VERSION = "graph-extraction-v1";
    static final String SCHEMA = """
        {"type":"object","properties":{"contractVersion":{"type":"string","enum":["graph-extraction-v1"]},"nodes":{"type":"array","items":{"type":"object","properties":{"label":{"type":"string"},"properties":{"type":"array","items":{"$ref":"#/$defs/entry"}},"confidence":{"type":["number","null"]}},"required":["label","properties","confidence"],"additionalProperties":false}},"relationships":{"type":"array","items":{"type":"object","properties":{"type":{"type":"string"},"fromLabel":{"type":"string"},"fromKey":{"type":"array","items":{"$ref":"#/$defs/entry"}},"toLabel":{"type":"string"},"toKey":{"type":"array","items":{"$ref":"#/$defs/entry"}},"properties":{"type":"array","items":{"$ref":"#/$defs/entry"}},"confidence":{"type":["number","null"]}},"required":["type","fromLabel","fromKey","toLabel","toKey","properties","confidence"],"additionalProperties":false}}},"required":["contractVersion","nodes","relationships"],"additionalProperties":false,"title":"graph-extraction-v1","$defs":{"value":{"anyOf":[{"type":"null"},{"type":"string"},{"type":"boolean"},{"type":"integer"},{"type":"number"},{"type":"array","items":{"$ref":"#/$defs/value"}},{"type":"object","properties":{"entries":{"type":"array","items":{"$ref":"#/$defs/entry"}}},"required":["entries"],"additionalProperties":false}]},"entry":{"type":"object","properties":{"key":{"type":"string"},"value":{"$ref":"#/$defs/value"}},"required":["key","value"],"additionalProperties":false}}}
        """;
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult decode(String content) {
        try {
            JsonNode root = mapper.readTree(content);
            requireObject(root);
            if (!VERSION.equals(string(root, "contractVersion"))) throw invalid();
            List<io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult.ExtractedNode> nodes = new ArrayList<>();
            for (JsonNode node : array(root.get("nodes"))) {
                requireObject(node);
                warnUnknown(node, Set.of("label", "properties", "confidence"));
                nodes.add(new io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult.ExtractedNode(
                    string(node, "label"), entries(node.get("properties")), confidence(node)));
            }
            List<io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult.ExtractedRelationship> relationships = new ArrayList<>();
            for (JsonNode rel : array(root.get("relationships"))) {
                requireObject(rel);
                warnUnknown(rel, Set.of("type", "fromLabel", "fromKey", "toLabel", "toKey", "properties", "confidence"));
                relationships.add(new io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult.ExtractedRelationship(
                    string(rel, "type"), string(rel, "fromLabel"), entries(rel.get("fromKey")),
                    string(rel, "toLabel"), entries(rel.get("toKey")), entries(rel.get("properties")), confidence(rel)));
            }
            return new io.github.vfedoriv.graphrag.documents.domain.extraction.GraphExtractionResult(nodes, relationships);
        } catch (Exception exception) { throw invalid(); }
    }
    private Double confidence(JsonNode node) {
        JsonNode value = node.get("confidence");
        if (value == null || (!value.isNumber() && !value.isNull())) throw invalid();
        if (value.isNull()) return null;
        double result = value.doubleValue();
        if (!Double.isFinite(result)) throw invalid();
        return result;
    }
    private void warnUnknown(JsonNode node, Set<String> allowed) {
        int count = 0;
        java.util.Iterator<String> names = node.fieldNames();
        while (names.hasNext()) if (!allowed.contains(names.next())) count++;
        if (count > 0) org.slf4j.LoggerFactory.getLogger(NativeGraphOutput.class)
            .warn("Graph extraction response contains unknown fields that will be ignored: count={}", count);
    }
    private Map<String, Object> entries(JsonNode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (JsonNode entry : array(node)) {
            requireObject(entry);
            if (entry.size() != 2 || !entry.has("value")) throw invalid();
            String key = string(entry, "key");
            if (result.containsKey(key)) throw invalid();
            result.put(key, value(entry.get("value")));
        }
        return result;
    }
    private Object value(JsonNode node) {
        if (node == null) throw invalid();
        if (node.isNull()) return null;
        if (node.isTextual()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.numberValue();
        if (node.isFloatingPointNumber()) {
            double value = node.doubleValue();
            if (!Double.isFinite(value)) throw invalid();
            return value;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonNode item : node) values.add(value(item));
            return values;
        }
        if (node.isObject() && node.size() == 1 && node.has("entries")) return entries(node.get("entries"));
        throw invalid();
    }
    private JsonNode array(JsonNode node) {
        if (node == null || !node.isArray()) throw invalid();
        return node;
    }
    private String string(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw invalid();
        return value.textValue();
    }
    private void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) throw invalid();
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("INVALID_NATIVE_OUTPUT"); }
}
