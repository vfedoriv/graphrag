package io.github.vfedoriv.graphrag.schemas.reprocessing.ports;

import java.util.Optional;

public interface ReprocessingKnowledgeBases {
    KnowledgeBase requireManaged(String knowledgeBaseId);
    Optional<KnowledgeBase> find(String knowledgeBaseId);
    Profile activeProfile(String knowledgeBaseId);

    record KnowledgeBase(String id, String activeSchemaId) { }
    record Profile(String id, long revision, String baseUrl, String embeddingModel,
                   Integer embeddingDimensions, String tokenizerId) { }
}
