package io.github.vfedoriv.graphrag.schemas.publication.contracts;

import java.time.Instant;
import java.util.Optional;

/** Immutable publication identity facts for other schema workflows. */
public interface PublicationFacts {
    Optional<Publication> findByDraftId(String draftId);
    Optional<Publication> findBySchemaId(String schemaId);

    record Publication(String id, String draftId, String knowledgeBaseId, String schemaId,
                       long draftRevision, String aggregateRevisionId, String projectionContentHash,
                       String targetIdentity, boolean completed, Instant createdAt) { }
}
