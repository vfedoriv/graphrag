package io.github.vfedoriv.graphrag.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SchemaParser {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public SchemaDocument parse(String yamlContent) {
        try {
            return mapper.readValue(yamlContent, SchemaDocument.class);
        } catch (IOException e) {
            log.error("Invalid schema YAML: contentLength={}, message={}", yamlContent == null ? 0 : yamlContent.length(), e.getMessage(), e);
            throw new IllegalArgumentException("Invalid schema YAML", e);
        }
    }
}
