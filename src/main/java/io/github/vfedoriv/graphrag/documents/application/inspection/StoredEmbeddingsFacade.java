package io.github.vfedoriv.graphrag.documents.application.inspection;

import io.github.vfedoriv.graphrag.documents.contracts.StoredEmbeddings;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StoredEmbeddingsFacade implements StoredEmbeddings {
    private final DocumentChunkRepository chunks;

    public StoredEmbeddingsFacade(DocumentChunkRepository chunks) {
        this.chunks = chunks;
    }

    @Override
    public List<Observation> observations(String knowledgeBaseId) {
        List<DocumentChunkNode> stored = chunks.findEmbeddedChunksByKnowledgeBaseId(knowledgeBaseId);
        return stored == null ? List.of() : stored.stream().map(chunk -> new Observation(
            chunk.getEmbeddingSpaceId(), chunk.getEmbeddingModel(), chunk.getTokenizerId())).toList();
    }
}
