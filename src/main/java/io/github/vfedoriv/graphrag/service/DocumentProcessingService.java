package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ExtractionRunRepository extractionRunRepository;
    private final DocumentUploadService documentUploadService;
    private final DocumentParsingService documentParsingService;
    private final ChunkingService chunkingService;
    private final Neo4jClient neo4jClient;
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final Environment environment;
    private final GraphExtractionService graphExtractionService;
    private final AiObservationService aiObservationService;
    private final KnowledgeBaseService knowledgeBaseService;

    public DocumentProcessingService(
        DocumentUploadRepository documentUploadRepository,
        DocumentChunkRepository documentChunkRepository,
        ExtractionRunRepository extractionRunRepository,
        DocumentUploadService documentUploadService,
        DocumentParsingService documentParsingService,
        ChunkingService chunkingService,
        Neo4jClient neo4jClient,
        ObjectProvider<EmbeddingClient> embeddingClientProvider,
        ObjectProvider<EmbeddingModel> embeddingModelProvider,
        Environment environment,
        GraphExtractionService graphExtractionService,
        AiObservationService aiObservationService,
        KnowledgeBaseService knowledgeBaseService
    ) {
        this.documentUploadRepository = documentUploadRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.extractionRunRepository = extractionRunRepository;
        this.documentUploadService = documentUploadService;
        this.documentParsingService = documentParsingService;
        this.chunkingService = chunkingService;
        this.neo4jClient = neo4jClient;
        this.embeddingClientProvider = embeddingClientProvider;
        this.embeddingModelProvider = embeddingModelProvider;
        this.environment = environment;
        this.graphExtractionService = graphExtractionService;
        this.aiObservationService = aiObservationService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public DocumentUploadNode process(String documentId) {
        return process(documentId, false);
    }

    public DocumentUploadNode process(String documentId, boolean allowOverwrite) {
        long startNanos = System.nanoTime();
        DocumentUploadNode document = documentUploadRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        if (!allowOverwrite && extractionRunRepository.hasCompletedRun(documentId)) {
            throw new ConflictException(
                "Document already has a completed extraction run. Set allowOverwrite=true to replace it."
            );
        }
        log.info(
            "Starting document processing: documentId={}, knowledgeBaseId={}, filename={}, contentType={}, sizeBytes={}, allowOverwrite={}",
            document.getId(),
            document.getKnowledgeBaseId(),
            document.getOriginalFilename(),
            document.getContentType(),
            document.getSizeBytes(),
            allowOverwrite
        );
        Map<String, String> workflowAttributes = new LinkedHashMap<>();
        workflowAttributes.put("document.id", String.valueOf(document.getId()));
        workflowAttributes.put("knowledge_base.id", String.valueOf(document.getKnowledgeBaseId()));
        workflowAttributes.put("document.size_bytes", String.valueOf(document.getSizeBytes()));
        workflowAttributes.put("document.allow_overwrite", String.valueOf(allowOverwrite));
        try (AiObservationScope workflow = aiObservationService.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_DOCUMENT_PROCESSING,
            null,
            workflowAttributes
        ))) {
            try {
            document = setStatus(document, DocumentStatus.PARSING, null);
            String text = parseDocument(document);
            List<String> chunks = chunkingService.split(text);
            workflow.highCardinalityAttribute("document.chunk_count", String.valueOf(chunks.size()));
            log.info("Document parsed and chunked: documentId={}, chunks={}", documentId, chunks.size());
            document = setStatus(document, DocumentStatus.EMBEDDING, null);

            AiProfileNode activeProfile = activeProfile(document.getKnowledgeBaseId());
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
                    activeProfile.getBaseUrl(),
                    activeProfile.getEmbeddingModel(),
                    activeProfile.getEmbeddingDimensions()
                );
                throw new IllegalStateException("Embedding model is not configured for this profile");
            }
            log.info(
                "Embedding client resolved: documentId={}, embeddingClientClass={}",
                documentId,
                embeddingClient.getClass().getName()
            );
            List<List<Double>> embeddings = AiProfileContext.withProfile(activeProfile.getId(), () -> embeddingClient.embed(chunks));
            log.info("Embedding request completed: documentId={}, vectors={}", documentId, embeddings.size());
            if (embeddings.size() != chunks.size()) {
                throw new IllegalStateException("Embedding response size mismatch");
            }

            ensureVectorIndex(activeProfile.getEmbeddingDimensions());
            documentChunkRepository.deleteByDocumentId(documentId);
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunkNode chunk = new DocumentChunkNode();
                chunk.setId(UUID.randomUUID().toString());
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(i);
                chunk.setText(chunks.get(i));
                chunk.setTokenEstimate(chunkingService.tokenEstimate(chunks.get(i)));
                chunk.setEmbedding(embeddings.get(i));
                chunk.setEmbeddingModel(activeProfile.getEmbeddingModel());
                chunk.setEmbeddingDimensions(activeProfile.getEmbeddingDimensions());
                chunk.setMetadata("{\"source\":\"" + safeJson(document.getOriginalFilename()) + "\"}");
                documentChunkRepository.save(chunk);
                createChunkRelationship(documentId, chunk.getId());
            }
            document = setStatus(document, DocumentStatus.EXTRACTING_GRAPH, null);
            List<DocumentChunkNode> persistedChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
            log.info("Starting graph extraction: documentId={}, persistedChunks={}", documentId, persistedChunks.size());
            DocumentUploadNode documentForExtraction = document;
            AiProfileContext.withProfile(
                activeProfile.getId(),
                () -> graphExtractionService.extract(documentForExtraction, persistedChunks, allowOverwrite)
            );

            document.setProcessedAt(Instant.now());
            log.info(
                "Document processing completed successfully: documentId={}, elapsedMs={}",
                documentId,
                LogSanitizer.elapsedMillis(startNanos)
            );
            workflow.success();
            return setStatus(document, DocumentStatus.COMPLETED, null);
        } catch (Exception ex) {
            workflow.error(ex);
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

    private AiProfileNode activeProfile(String knowledgeBaseId) {
        return knowledgeBaseService.activeAiProfile(knowledgeBaseId);
    }

    private void ensureVectorIndex(int embeddingDimensions) {
        log.info("Ensuring vector index exists: index={}, dimensions={}", CHUNK_EMBEDDING_INDEX, embeddingDimensions);
        neo4jClient.query("""
            CREATE VECTOR INDEX %s IF NOT EXISTS
            FOR (c:DocumentChunk)
            ON (c.embedding)
            OPTIONS {indexConfig: {
              `vector.dimensions`: $dimensions,
              `vector.similarity_function`: 'cosine'
            }}
            """.formatted(CHUNK_EMBEDDING_INDEX))
            .bind(embeddingDimensions).to("dimensions")
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
