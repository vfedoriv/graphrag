package io.github.vfedoriv.graphrag.bootstrap.integration.search;

import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.schemas.contracts.CapturedSchemaParsing;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshots;
import io.github.vfedoriv.graphrag.schemas.contracts.StoredSchemaSnapshots;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchSchemas;
import org.springframework.stereotype.Component;

@Component
public class SearchSchemaAdapter implements SearchSchemas {
    private final StoredSchemaSnapshots storedSchemas;
    private final SchemaSnapshots activeSchemas;
    private final CapturedSchemaParsing capturedParsing;

    public SearchSchemaAdapter(
        StoredSchemaSnapshots storedSchemas,
        SchemaSnapshots activeSchemas,
        CapturedSchemaParsing capturedParsing
    ) {
        this.storedSchemas = storedSchemas;
        this.activeSchemas = activeSchemas;
        this.capturedParsing = capturedParsing;
    }

    @Override
    public boolean available(String schemaId) {
        return storedSchemas.findById(schemaId).isPresent();
    }

    @Override
    public SchemaSnapshot capture(String schemaId) {
        return storedSchemas.findById(schemaId)
            .orElseThrow(() -> new NotFoundException("Schema not found: " + schemaId));
    }

    @Override
    public SchemaSnapshot resolveActive(String knowledgeBaseId) {
        return activeSchemas.resolveActive(knowledgeBaseId);
    }

    @Override
    public SchemaSnapshot parseCaptured(String knowledgeBaseId, String schemaId, String contentHash, String content) {
        return capturedParsing.parseCaptured(knowledgeBaseId, schemaId, contentHash, content);
    }
}
