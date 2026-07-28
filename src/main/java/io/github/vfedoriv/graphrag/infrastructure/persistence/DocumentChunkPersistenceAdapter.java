package io.github.vfedoriv.graphrag.infrastructure.persistence;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIndexService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunkPersistenceAdapter {

    private final DocumentChunkRepository repository;
    private final EmbeddingSpaceIndexService embeddingSpaceIndexService;

    public DocumentChunkPersistenceAdapter(
        DocumentChunkRepository repository,
        EmbeddingSpaceIndexService embeddingSpaceIndexService
    ) {
        this.repository = repository;
        this.embeddingSpaceIndexService = embeddingSpaceIndexService;
    }

    public void replace(String documentId, String knowledgeBaseId, EmbeddingSpace embeddingSpace, List<DocumentChunkNode> chunks) {
        requireScope(documentId, knowledgeBaseId, chunks);
        embeddingSpaceIndexService.ensureIndex(knowledgeBaseId, embeddingSpace);
        repository.deleteByDocumentId(documentId);
        for (DocumentChunkNode chunk : chunks) {
            repository.save(chunk);
            embeddingSpaceIndexService.assignChunk(chunk.getId(), knowledgeBaseId, embeddingSpace);
        }
    }

    public List<DocumentChunkNode> findByDocumentId(String documentId) {
        return repository.findByDocumentIdOrderByChunkIndexAsc(documentId);
    }

    private void requireScope(String documentId, String knowledgeBaseId, List<DocumentChunkNode> chunks) {
        requireNonBlank(documentId, "documentId");
        requireNonBlank(knowledgeBaseId, "knowledgeBaseId");
        for (DocumentChunkNode chunk : chunks) {
            if (!knowledgeBaseId.equals(chunk.getKnowledgeBaseId()) || !documentId.equals(chunk.getDocumentId())) {
                throw new IllegalArgumentException("Chunk scope must match the persistence boundary");
            }
        }
    }

    private void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
