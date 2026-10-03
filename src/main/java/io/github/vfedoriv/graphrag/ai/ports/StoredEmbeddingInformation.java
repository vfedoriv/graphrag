package io.github.vfedoriv.graphrag.ai.ports;

import io.github.vfedoriv.graphrag.ai.domain.StoredEmbeddingObservation;
import java.util.List;

public interface StoredEmbeddingInformation {
    List<StoredEmbeddingObservation> observations(String knowledgeBaseId);
}
