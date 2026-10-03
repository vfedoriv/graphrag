package io.github.vfedoriv.graphrag.search.query.adapters.model;

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
final class NativeCypherOutput {
    static final String VERSION = "cypher-generation-v1";
    static final String SCHEMA = """
        {"type":"object","properties":{"contractVersion":{"type":"string","enum":["cypher-generation-v1"]},"cypher":{"type":"string"},"explanation":{"type":"string"},"parameters":{"type":"array","items":{"$ref":"#/$defs/entry"}}},"required":["contractVersion","cypher","explanation","parameters"],"additionalProperties":false,"title":"cypher-generation-v1","$defs":{"value":{"anyOf":[{"type":"null"},{"type":"string"},{"type":"boolean"},{"type":"integer"},{"type":"number"},{"type":"array","items":{"$ref":"#/$defs/value"}},{"type":"object","properties":{"entries":{"type":"array","items":{"$ref":"#/$defs/entry"}}},"required":["entries"],"additionalProperties":false}]},"entry":{"type":"object","properties":{"key":{"type":"string"},"value":{"$ref":"#/$defs/value"}},"required":["key","value"],"additionalProperties":false}}}
        """;
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    io.github.vfedoriv.graphrag.search.query.domain.GeneratedCypher decode(String content) {
        try {
            JsonNode root = mapper.readTree(content);
            requireObject(root);
            if (root.size() != 4 || !VERSION.equals(string(root, "contractVersion"))) throw invalid();
            return new io.github.vfedoriv.graphrag.search.query.domain.GeneratedCypher(
                string(root, "cypher"), string(root, "explanation"), entries(root.get("parameters")));
        } catch (Exception exception) { throw invalid(); }
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
