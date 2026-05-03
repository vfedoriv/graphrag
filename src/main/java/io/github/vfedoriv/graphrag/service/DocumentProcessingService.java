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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessingService {

    public static final String CHUNK_EMBEDDING_INDEX = "document_chunk_embedding";
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final DocumentUploadRepository documentUploadRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentUploadService documentUploadService;
    private final DocumentParsingService documentParsingService;
    private final ChunkingService chunkingService;
    private final Neo4jClient neo4jClient;
    private final AppProperties appProperties;
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider;
    private final GraphExtractionService graphExtractionService;

    public DocumentProcessingService(
        DocumentUploadRepository documentUploadRepository,
        DocumentChunkRepository documentChunkRepository,
        DocumentUploadService documentUploadService,
        DocumentParsingService documentParsingService,
        ChunkingService chunkingService,
        Neo4jClient neo4jClient,
        AppProperties appProperties,
        ObjectProvider<EmbeddingClient> embeddingClientProvider,
        GraphExtractionService graphExtractionService
    ) {
        this.documentUploadRepository = documentUploadRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentUploadService = documentUploadService;
        this.documentParsingService = documentParsingService;
        this.chunkingService = chunkingService;
        this.neo4jClient = neo4jClient;
        this.appProperties = appProperties;
        this.embeddingClientProvider = embeddingClientProvider;
        this.graphExtractionService = graphExtractionService;
    }

    public DocumentUploadNode process(String documentId) {
        DocumentUploadNode document = documentUploadRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        log.info(
            "Starting document processing: documentId={}, knowledgeBaseId={}, filename={}, contentType={}, sizeBytes={}",
            document.getId(),
            document.getKnowledgeBaseId(),
            document.getOriginalFilename(),
            document.getContentType(),
            document.getSizeBytes()
        );
        try {
            setStatus(document, DocumentStatus.PARSING, null);
            String text = parseDocument(document);
            List<String> chunks = chunkingService.split(text);
            log.info("Document parsed and chunked: documentId={}, chunks={}", documentId, chunks.size());
            setStatus(document, DocumentStatus.EMBEDDING, null);

            EmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
            if (embeddingClient == null) {
                log.error(
                    "Embedding client is missing: documentId={}, profile model config baseUrl={}, embeddingModel={}, embeddingDimensions={}",
                    documentId,
                    appProperties.model().baseUrl(),
                    appProperties.model().embeddingModel(),
                    appProperties.model().embeddingDimensions()
                );
                throw new IllegalStateException("Embedding model is not configured for this profile");
            }
            log.info(
                "Embedding client resolved: documentId={}, embeddingClientClass={}",
                documentId,
                embeddingClient.getClass().getName()
            );
            List<List<Double>> embeddings = embeddingClient.embed(chunks);
            log.info("Embedding request completed: documentId={}, vectors={}", documentId, embeddings.size());
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
            setStatus(document, DocumentStatus.EXTRACTING_GRAPH, null);
            List<DocumentChunkNode> persistedChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
            log.info("Starting graph extraction: documentId={}, persistedChunks={}", documentId, persistedChunks.size());
            graphExtractionService.extract(document, persistedChunks);

            document.setProcessedAt(Instant.now());
            log.info("Document processing completed successfully: documentId={}", documentId);
            return setStatus(document, DocumentStatus.COMPLETED, null);
        } catch (Exception ex) {
            log.error("Document processing failed: documentId={}, message={}", documentId, ex.getMessage(), ex);
            return setStatus(document, DocumentStatus.FAILED, ex.getMessage());
        }
    }

    public List<DocumentChunkNode> getDocumentChunks(String documentId) {
        return documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
    }

    private String parseDocument(DocumentUploadNode document) throws IOException {
        byte[] bytes = documentUploadService.readContent(document.getContentUri());
        log.info("Loaded document bytes from storage: documentId={}, bytes={}", document.getId(), bytes.length);
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
