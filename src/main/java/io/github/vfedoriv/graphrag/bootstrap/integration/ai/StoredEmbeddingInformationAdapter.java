package io.github.vfedoriv.graphrag.bootstrap.integration.ai;

import io.github.vfedoriv.graphrag.ai.domain.StoredEmbeddingObservation;
import io.github.vfedoriv.graphrag.ai.ports.StoredEmbeddingInformation;
import io.github.vfedoriv.graphrag.documents.contracts.StoredEmbeddings;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StoredEmbeddingInformationAdapter implements StoredEmbeddingInformation {
    private final StoredEmbeddings documents;

    public StoredEmbeddingInformationAdapter(StoredEmbeddings documents) {
        this.documents = documents;
    }

    @Override
    public List<StoredEmbeddingObservation> observations(String knowledgeBaseId) {
        return documents.observations(knowledgeBaseId).stream().map(stored -> new StoredEmbeddingObservation(
            stored.embeddingSpaceId(), stored.embeddingModel(), stored.tokenizerId())).toList();
    }
}
