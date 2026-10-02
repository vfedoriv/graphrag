package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import java.util.List;
import java.util.Optional;

public interface DraftSchemaLookup {
    /** Raw stored facts; this lookup does not parse the definition. */
    Optional<SchemaSnapshot> findById(String schemaId);

    /** Parsed stored definition for review inheritance. */
    Optional<SchemaSnapshot> findParsedById(String schemaId);
    List<SchemaSnapshot> associated(String knowledgeBaseId);
}
