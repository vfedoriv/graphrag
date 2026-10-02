package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.contracts.StoredSchemaSnapshots;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.DraftSchemaLookup;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DraftSchemaLookupAdapter implements DraftSchemaLookup {
    private final StoredSchemaSnapshots schemas;

    public DraftSchemaLookupAdapter(StoredSchemaSnapshots schemas) {
        this.schemas = schemas;
    }

    @Override
    public Optional<SchemaSnapshot> findById(String schemaId) {
        return schemas.findById(schemaId);
    }

    @Override
    public Optional<SchemaSnapshot> findParsedById(String schemaId) {
        return schemas.findParsedById(schemaId);
    }

    @Override
    public List<SchemaSnapshot> associated(String knowledgeBaseId) {
        return schemas.associated(knowledgeBaseId);
    }
}
