package io.github.vfedoriv.graphrag.schemas.registry.application;

import io.github.vfedoriv.graphrag.schemas.contracts.CapturedSchemaParsing;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import org.springframework.stereotype.Service;

@Service
public class CapturedSchemaParsingFacade implements CapturedSchemaParsing {
    private final SchemaParser parser;

    public CapturedSchemaParsingFacade(SchemaParser parser) {
        this.parser = parser;
    }

    @Override
    public SchemaSnapshot parseCaptured(String knowledgeBaseId, String schemaId, String contentHash, String content) {
        SchemaDocument schema = parser.parse(content);
        return new SchemaSnapshot(
            knowledgeBaseId,
            schemaId,
            schema.name(),
            schema.version(),
            null,
            null,
            null,
            content,
            contentHash,
            null,
            null,
            schema
        );
    }
}
