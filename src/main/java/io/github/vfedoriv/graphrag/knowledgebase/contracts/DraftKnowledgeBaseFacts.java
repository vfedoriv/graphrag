package io.github.vfedoriv.graphrag.knowledgebase.contracts;

import java.util.Optional;

/** Knowledge-base admission and non-secret profile facts for draft authoring. */
public interface DraftKnowledgeBaseFacts {
    void requireManaged(String knowledgeBaseId);
    Optional<String> activeSchemaId(String knowledgeBaseId);
    Profile activeProfile(String knowledgeBaseId);

    record Profile(String id, long revision, int timeoutSeconds, int maxRetries) { }
}
