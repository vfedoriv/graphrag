package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.config.AppProperties;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@Slf4j
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
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final Environment environment;
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
        ObjectProvider<EmbeddingModel> embeddingModelProvider,
        Environment environment,
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
        this.embeddingModelProvider = embeddingModelProvider;
        this.environment = environment;
        this.graphExtractionService = graphExtractionService;
    }

    public DocumentUploadNode process(String documentId) {
        long startNanos = System.nanoTime();
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
            document = setStatus(document, DocumentStatus.PARSING, null);
            String text = parseDocument(document);
            List<String> chunks = chunkingService.split(text);
            log.info("Document parsed and chunked: documentId={}, chunks={}", documentId, chunks.size());
            document = setStatus(document, DocumentStatus.EMBEDDING, null);

            EmbeddingClient embeddingClient = resolveEmbeddingClient();
            if (embeddingClient == null) {
                List<String> embeddingModelBeans = embeddingModelProvider.stream()
                    .map(model -> model.getClass().getName())
                    .toList();
                log.error(
                    "Embedding client is missing: documentId={}, activeProfiles={}, spring.ai.model.embedding={}, modelBeans={}, profile model config baseUrl={}, embeddingModel={}, embeddingDimensions={}",
                    documentId,
                    Arrays.toString(environment.getActiveProfiles()),
                    environment.getProperty("spring.ai.model.embedding"),
                    embeddingModelBeans,
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
            document = setStatus(document, DocumentStatus.EXTRACTING_GRAPH, null);
            List<DocumentChunkNode> persistedChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
            log.info("Starting graph extraction: documentId={}, persistedChunks={}", documentId, persistedChunks.size());
            graphExtractionService.extract(document, persistedChunks);

            document.setProcessedAt(Instant.now());
            log.info(
                "Document processing completed successfully: documentId={}, elapsedMs={}",
                documentId,
                LogSanitizer.elapsedMillis(startNanos)
            );
            return setStatus(document, DocumentStatus.COMPLETED, null);
        } catch (Exception ex) {
            log.error(
                "Document processing failed: documentId={}, elapsedMs={}, message={}",
                documentId,
                LogSanitizer.elapsedMillis(startNanos),
                ex.getMessage(),
                ex
            );
            return setStatus(document, DocumentStatus.FAILED, ex.getMessage());
        }
    }

    public List<DocumentChunkNode> getDocumentChunks(String documentId) {
        log.info("Loading document chunks: documentId={}", documentId);
        List<DocumentChunkNode> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        log.info("Document chunks loaded: documentId={}, count={}", documentId, chunks.size());
        return chunks;
    }

    private String parseDocument(DocumentUploadNode document) throws IOException {
        byte[] bytes = documentUploadService.readContent(document.getContentUri());
        log.info("Loaded document bytes from storage: documentId={}, bytes={}", document.getId(), bytes.length);
        return documentParsingService.parse(document.getOriginalFilename(), document.getContentType(), bytes);
    }

    private DocumentUploadNode setStatus(DocumentUploadNode document, DocumentStatus status, String errorMessage) {
        log.info("Updating document status: documentId={}, status={}", document.getId(), status);
        document.setStatus(status);
        document.setErrorMessage(errorMessage);
        return documentUploadRepository.save(document);
    }

    private void ensureVectorIndex() {
        log.info("Ensuring vector index exists: index={}, dimensions={}", CHUNK_EMBEDDING_INDEX, appProperties.model().embeddingDimensions());
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
        log.info("Vector index ensured: index={}", CHUNK_EMBEDDING_INDEX);
    }

    private void createChunkRelationship(String documentId, String chunkId) {
        neo4jClient.query("""
            MATCH (d:DocumentUpload {id: $documentId})
            MATCH (c:DocumentChunk {id: $chunkId})
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

    private EmbeddingClient resolveEmbeddingClient() {
        List<EmbeddingClient> clients = embeddingClientProvider.orderedStream().toList();
        if (clients.isEmpty()) {
            return null;
        }
        if (clients.size() == 1) {
            return clients.getFirst();
        }
        return clients.stream()
            .filter(client -> !client.getClass().getName().contains("SpringAi"))
            .findFirst()
            .orElse(clients.getFirst());
    }
}
