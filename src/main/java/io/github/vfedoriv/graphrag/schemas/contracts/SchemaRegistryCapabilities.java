package io.github.vfedoriv.graphrag.schemas.contracts;

import java.util.Optional;

/** Immutable registry facts and operations shared by schema-owned workflows. */
public interface SchemaRegistryCapabilities {
    SchemaValidationResult parseAndValidate(String json);

    /** Checks the registry-wide name/version namespace, independent of any knowledge-base association. */
    boolean identityExistsGlobally(String name, int version);

    /** Finds a schema only when that identity is associated with the requested knowledge base. */
    Optional<SchemaSnapshot> findAssociatedByIdentity(String knowledgeBaseId, String name, int version);

    /** Returns exact stored content/hash and the registry-wide active status, if the schema exists. */
    Optional<SchemaSnapshot> findStoredById(String schemaDefinitionId);

    /** Creates and associates a generated schema using the ordinary inactive registry path. */
    SchemaSnapshot registerGeneratedInactive(String json, String knowledgeBaseId);
}
