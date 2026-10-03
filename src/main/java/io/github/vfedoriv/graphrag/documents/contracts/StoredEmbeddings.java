package io.github.vfedoriv.graphrag.documents.contracts;

import java.util.List;

public interface StoredEmbeddings {
    List<Observation> observations(String knowledgeBaseId);

    record Observation(String embeddingSpaceId, String embeddingModel, String tokenizerId) { }
}
