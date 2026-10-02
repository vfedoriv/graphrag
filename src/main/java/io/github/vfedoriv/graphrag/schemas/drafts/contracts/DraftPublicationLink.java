package io.github.vfedoriv.graphrag.schemas.drafts.contracts;

import java.time.Instant;

/** Authoring-owned linkage participating in the caller's completion transaction. */
public interface DraftPublicationLink {
    void complete(Completion completion);

    record Completion(String knowledgeBaseId, String draftId, long draftRevision, String aggregateRevisionId,
                      Long persistenceVersion, String schemaId, String contentHash, Instant completedAt) { }
}
