package io.github.vfedoriv.graphrag.schemas.registry.application;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
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
            log.error("Invalid schema JSON: contentLength={}, exceptionType={}", jsonContent == null ? 0 : jsonContent.length(), LogMetadata.exceptionType(e));
            throw new IllegalArgumentException("Invalid schema JSON", e);
        }
    }
}
