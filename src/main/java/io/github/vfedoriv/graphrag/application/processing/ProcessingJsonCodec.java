package io.github.vfedoriv.graphrag.application.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public final class ProcessingJsonCodec {

    private final ObjectMapper objectMapper;

    public ProcessingJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Stored document processing defaults JSON is invalid", ex);
        }
    }

    public String writeMap(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? Map.of() : values);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Document processing options JSON is invalid", ex);
        }
    }
}
