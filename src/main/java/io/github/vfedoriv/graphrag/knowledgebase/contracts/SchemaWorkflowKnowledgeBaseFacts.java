package io.github.vfedoriv.graphrag.knowledgebase.contracts;

import java.util.Optional;

/** Admission, current schema and non-secret execution identity for schema workflows. */
public interface SchemaWorkflowKnowledgeBaseFacts {
    KnowledgeBase requireManaged(String knowledgeBaseId);
    Optional<KnowledgeBase> find(String knowledgeBaseId);
    Profile activeProfile(String knowledgeBaseId);

    record KnowledgeBase(String id, String activeSchemaId) { }
    record Profile(String id, long revision, String baseUrl, String embeddingModel,
                   Integer embeddingDimensions, String tokenizerId) { }
}
