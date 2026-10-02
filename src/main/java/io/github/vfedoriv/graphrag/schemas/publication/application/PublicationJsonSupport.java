package io.github.vfedoriv.graphrag.schemas.publication.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class PublicationJsonSupport {
    private final ObjectMapper objectMapper;

    public PublicationJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String canonical(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(sort(value == null ? objectMapper.createObjectNode() : value));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Value cannot be serialized", exception);
        }
    }

    public String canonical(Object value) {
        return canonical(objectMapper.valueToTree(value));
    }

    public JsonNode parse(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted draft JSON is invalid", exception);
        }
    }

    public <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted draft JSON is invalid", exception);
        }
    }

    public String fingerprint(String canonicalValue) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonicalValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String fingerprint(JsonNode value) {
        return fingerprint(canonical(value));
    }

    private JsonNode sort(JsonNode value) {
        if (value.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            value.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, child) -> sorted.set(key, sort(child)));
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            value.forEach(child -> sorted.add(sort(child)));
            return sorted;
        }
        return value;
    }
}
