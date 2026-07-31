package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AdvancedSearchResultCodec {
    public static final int PAYLOAD_VERSION = 1;
    private final ObjectMapper objectMapper;
    public AdvancedSearchResultCodec(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public String write(JsonNode result, int evidenceCount, int maximumEvidence) {
        if (result == null || !result.isObject()) {
            throw new IllegalArgumentException("Advanced-search result must be a JSON object");
        }
        if (result.path("payloadVersion").asInt(-1) != PAYLOAD_VERSION) {
            throw new IllegalArgumentException("Unsupported advanced-search result payload version");
        }
        if (evidenceCount < 0 || evidenceCount > maximumEvidence || evidenceCount > 20) {
            throw new IllegalArgumentException("Advanced-search result exceeds its bounded evidence envelope");
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Advanced-search result is not serializable", exception);
        }
    }

    public JsonNode read(String json) {
        try {
            JsonNode value = objectMapper.readTree(json);
            if (!value.isObject() || value.path("payloadVersion").asInt(-1) != PAYLOAD_VERSION) {
                throw new IllegalArgumentException("Stored advanced-search result is invalid");
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Stored advanced-search result is invalid", exception);
        }
    }
}
