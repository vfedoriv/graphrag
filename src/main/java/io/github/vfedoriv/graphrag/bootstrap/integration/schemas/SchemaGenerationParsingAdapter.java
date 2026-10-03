package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentSourceInputs;
import io.github.vfedoriv.graphrag.schemas.generation.ports.SchemaGenerationParsing;
import org.springframework.stereotype.Component;

@Component
public class SchemaGenerationParsingAdapter implements SchemaGenerationParsing {
    private final DocumentSourceInputs documents;

    public SchemaGenerationParsingAdapter(DocumentSourceInputs documents) {
        this.documents = documents;
    }

    @Override
    public String parse(String filename, String contentType, byte[] bytes) {
        return documents.parse(filename, contentType, bytes);
    }
}
