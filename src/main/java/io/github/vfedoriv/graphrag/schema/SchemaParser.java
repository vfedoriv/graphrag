package io.github.vfedoriv.graphrag.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SchemaParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public SchemaDocument parse(String jsonContent) {
        try {
            return mapper.readValue(jsonContent, SchemaDocument.class);
        } catch (IOException e) {
            log.error("Invalid schema JSON: contentLength={}, message={}", jsonContent == null ? 0 : jsonContent.length(), e.getMessage(), e);
            throw new IllegalArgumentException("Invalid schema JSON", e);
        }
    }
}
