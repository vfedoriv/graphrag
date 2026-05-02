package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessingService {

    public static final String CHUNK_EMBEDDING_INDEX = "document_chunk_embedding";

    private final DocumentUploadRepository documentUploadRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentUploadService documentUploadService;
    private final DocumentParsingService documentParsingService;
    private final ChunkingService chunkingService;
    private final Neo4jClient neo4jClient;
    private final AppProperties appProperties;
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider;

    public DocumentProcessingService(
        DocumentUploadRepository documentUploadRepository,
        DocumentChunkRepository documentChunkRepository,
        DocumentUploadService documentUploadService,
        DocumentParsingService documentParsingService,
        ChunkingService chunkingService,
        Neo4jClient neo4jClient,
        AppProperties appProperties,
        ObjectProvider<EmbeddingClient> embeddingClientProvider
    ) {
        this.documentUploadRepository = documentUploadRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentUploadService = documentUploadService;
        this.documentParsingService = documentParsingService;
        this.chunkingService = chunkingService;
        this.neo4jClient = neo4jClient;
        this.appProperties = appProperties;
        this.embeddingClientProvider = embeddingClientProvider;
    }

    public DocumentUploadNode process(String documentId) {
        DocumentUploadNode document = documentUploadRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        try {
            setStatus(document, DocumentStatus.PARSING, null);
            String text = parseDocument(document);
            List<String> chunks = chunkingService.split(text);
            setStatus(document, DocumentStatus.EMBEDDING, null);

            EmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
            if (embeddingClient == null) {
                throw new IllegalStateException("Embedding model is not configured for this profile");
            }
            List<List<Double>> embeddings = embeddingClient.embed(chunks);
            if (embeddings.size() != chunks.size()) {
                throw new IllegalStateException("Embedding response size mismatch");
            }

            ensureVectorIndex();
            documentChunkRepository.deleteByDocumentId(documentId);
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunkNode chunk = new DocumentChunkNode();
                chunk.setId(UUID.randomUUID().toString());
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(i);
                chunk.setText(chunks.get(i));
                chunk.setTokenEstimate(chunkingService.tokenEstimate(chunks.get(i)));
                chunk.setEmbedding(embeddings.get(i));
                chunk.setMetadata("{\"source\":\"" + safeJson(document.getOriginalFilename()) + "\"}");
                documentChunkRepository.save(chunk);
                createChunkRelationship(documentId, chunk.getId());
            }

            document.setProcessedAt(Instant.now());
            return setStatus(document, DocumentStatus.COMPLETED, null);
        } catch (Exception ex) {
            return setStatus(document, DocumentStatus.FAILED, ex.getMessage());
        }
    }

    private String parseDocument(DocumentUploadNode document) throws IOException {
        byte[] bytes = documentUploadService.readContent(document.getContentUri());
        return documentParsingService.parse(document.getOriginalFilename(), document.getContentType(), bytes);
    }

    private DocumentUploadNode setStatus(DocumentUploadNode document, DocumentStatus status, String errorMessage) {
        document.setStatus(status);
        document.setErrorMessage(errorMessage);
        return documentUploadRepository.save(document);
    }

    private void ensureVectorIndex() {
        neo4jClient.query("""
            CREATE VECTOR INDEX %s IF NOT EXISTS
            FOR (c:DocumentChunk)
            ON (c.embedding)
            OPTIONS {indexConfig: {
              `vector.dimensions`: $dimensions,
              `vector.similarity_function`: 'cosine'
            }}
            """.formatted(CHUNK_EMBEDDING_INDEX))
            .bind(appProperties.model().embeddingDimensions()).to("dimensions")
            .run();
    }

    private void createChunkRelationship(String documentId, String chunkId) {
        neo4jClient.query("""
            MATCH (d:DocumentUpload {id: $documentId}), (c:DocumentChunk {id: $chunkId})
            MERGE (d)-[:HAS_CHUNK]->(c)
            """)
            .bind(documentId).to("documentId")
            .bind(chunkId).to("chunkId")
            .run();
    }

    private String safeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
