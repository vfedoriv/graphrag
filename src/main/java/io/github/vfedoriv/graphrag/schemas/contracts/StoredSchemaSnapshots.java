package io.github.vfedoriv.graphrag.schemas.contracts;

import java.util.List;
import java.util.Optional;

/** Stored definitions and scope-specific association status for schema consumers. */
public interface StoredSchemaSnapshots {
    /** Raw stored facts; this lookup does not parse the definition. */
    Optional<SchemaSnapshot> findById(String schemaId);

    /** Parsed stored definition for review inheritance. */
    Optional<SchemaSnapshot> findParsedById(String schemaId);
    List<SchemaSnapshot> associated(String knowledgeBaseId);
}
