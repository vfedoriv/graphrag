package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import java.util.Optional;

public interface DraftKnowledgeBases {
    void requireManaged(String knowledgeBaseId);
    Optional<String> activeSchemaId(String knowledgeBaseId);
    Profile activeProfile(String knowledgeBaseId);

    record Profile(String id, long revision, int timeoutSeconds, int maxRetries) { }
}
