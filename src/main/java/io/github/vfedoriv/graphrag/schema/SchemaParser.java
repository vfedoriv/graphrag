package io.github.vfedoriv.graphrag.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class SchemaParser {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public SchemaDocument parse(String yamlContent) {
        try {
            return mapper.readValue(yamlContent, SchemaDocument.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid schema YAML", e);
        }
    }
}
