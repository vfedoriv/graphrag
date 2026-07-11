package io.github.vfedoriv.graphrag.infrastructure.persistence;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIndexService;
import java.util.List;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunkPersistenceAdapter {

    private final DocumentChunkRepository repository;
    private final Neo4jClient neo4jClient;
    private final EmbeddingSpaceIndexService embeddingSpaceIndexService;

    public DocumentChunkPersistenceAdapter(
        DocumentChunkRepository repository,
        Neo4jClient neo4jClient,
        EmbeddingSpaceIndexService embeddingSpaceIndexService
    ) {
        this.repository = repository;
        this.neo4jClient = neo4jClient;
        this.embeddingSpaceIndexService = embeddingSpaceIndexService;
    }

    public void replace(String documentId, String knowledgeBaseId, EmbeddingSpace embeddingSpace, List<DocumentChunkNode> chunks) {
        embeddingSpaceIndexService.ensureIndex(knowledgeBaseId, embeddingSpace);
        repository.deleteByDocumentId(documentId);
        for (DocumentChunkNode chunk : chunks) {
            repository.save(chunk);
            attachToDocument(documentId, chunk.getId());
            embeddingSpaceIndexService.assignChunk(chunk.getId(), knowledgeBaseId, embeddingSpace);
        }
    }

    public List<DocumentChunkNode> findByDocumentId(String documentId) {
        return repository.findByDocumentIdOrderByChunkIndexAsc(documentId);
    }

    private void attachToDocument(String documentId, String chunkId) {
        neo4jClient.query("""
            MATCH (d:DocumentUpload {id: $documentId})
            MATCH (c:DocumentChunk {id: $chunkId})
            MERGE (d)-[:HAS_CHUNK]->(c)
            """)
            .bind(documentId).to("documentId")
            .bind(chunkId).to("chunkId")
            .run();
    }
}
