package io.github.vfedoriv.graphrag.search.runs.ports;

import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;

/** Non-secret profile inspection and local client construction checks. */
public interface SearchProfiles {
    Profile resolve(String profileId);
    Profile forKnowledgeBase(String knowledgeBaseId);
    void constructChat(String profileId);
    void constructEmbedding(String profileId);
    record Profile(String id, long revision, String baseUrl, String chatModel,
                   String embeddingModel, int embeddingDimensions, String tokenizerId) {
        public EmbeddingTarget target() {
            return EmbeddingTarget.derive(baseUrl, embeddingModel, embeddingDimensions, tokenizerId);
        }
    }
}
