package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.document.ParsedDocument;
import io.github.vfedoriv.graphrag.document.ParsedSection;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunStatus;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.dto.DocumentProcessingOptionConstraintResponse;
import io.github.vfedoriv.graphrag.dto.DocumentProcessingOptionResponse;
import io.github.vfedoriv.graphrag.dto.DocumentProcessingOptionsResponse;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.logging.LogSanitizer;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentProcessingRunRepository;
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
    private final DocumentProcessingRunRepository documentProcessingRunRepository;
    private final DocumentUploadService documentUploadService;
    private final DocumentParsingService documentParsingService;
    private final ChunkingService chunkingService;
    private final DocumentProcessingOptionsRegistry processingOptionsRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
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
        DocumentProcessingRunRepository documentProcessingRunRepository,
        DocumentUploadService documentUploadService,
        DocumentParsingService documentParsingService,
        ChunkingService chunkingService,
        DocumentProcessingOptionsRegistry processingOptionsRegistry,
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
        this.documentProcessingRunRepository = documentProcessingRunRepository;
        this.documentUploadService = documentUploadService;
        this.documentParsingService = documentParsingService;
        this.chunkingService = chunkingService;
        this.processingOptionsRegistry = processingOptionsRegistry;
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
        return process(documentId, allowOverwrite, Map.of());
    }

    public DocumentUploadNode process(String documentId, boolean allowOverwrite, Map<String, Object> requestedOptions) {
        long startNanos = System.nanoTime();
        DocumentUploadNode document = documentUploadRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        DocumentProcessingOptionSet optionSet = resolveOptionSet(document, requestedOptions);
        if (!allowOverwrite && Boolean.TRUE.equals(extractionRunRepository.hasCompletedRun(documentId))) {
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
        workflowAttributes.put("document.parser_id", optionSet.detection().parserId());
        workflowAttributes.put("document.file_format", optionSet.detection().fileFormat());
        try (AiObservationScope workflow = aiObservationService.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_DOCUMENT_PROCESSING,
            null,
            workflowAttributes
        ))) {
            DocumentProcessingRunNode processingRun = createProcessingRun(document, optionSet);
            try {
                updateProcessingRunStage(processingRun, "PARSING");
                document = setStatus(document, DocumentStatus.PARSING, null);
                ParsedDocument parsedDocument = parseDocument(document, optionSet.effectiveOptions());
                updateProcessingRunStage(processingRun, "CHUNKING");
                List<ChunkWithMetadata> chunks = chunksFor(document, processingRun, parsedDocument);
                List<String> chunkTexts = chunks.stream().map(ChunkWithMetadata::text).toList();
                workflow.highCardinalityAttribute("document.chunk_count", String.valueOf(chunks.size()));
                log.info("Document parsed and chunked: documentId={}, chunks={}", documentId, chunks.size());
                updateProcessingRunStage(processingRun, "EMBEDDING");
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
                List<List<Double>> embeddings = AiProfileContext.withProfile(activeProfile.getId(), () -> embeddingClient.embed(chunkTexts));
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
                    chunk.setText(chunks.get(i).text());
                    chunk.setTokenEstimate(chunkingService.tokenEstimate(chunks.get(i).text()));
                    chunk.setEmbedding(embeddings.get(i));
                    chunk.setEmbeddingModel(activeProfile.getEmbeddingModel());
                    chunk.setEmbeddingDimensions(activeProfile.getEmbeddingDimensions());
                    chunk.setMetadata(writeJson(chunks.get(i).metadata()));
                    documentChunkRepository.save(chunk);
                    createChunkRelationship(documentId, chunk.getId());
                }
                document = setStatus(document, DocumentStatus.EXTRACTING_GRAPH, null);
                List<DocumentChunkNode> persistedChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
                log.info("Starting graph extraction: documentId={}, persistedChunks={}", documentId, persistedChunks.size());
                updateProcessingRunStage(processingRun, "EXTRACTING_GRAPH");
                DocumentUploadNode documentForExtraction = document;
                AiProfileContext.withProfile(
                    activeProfile.getId(),
                    () -> graphExtractionService.extract(documentForExtraction, persistedChunks, allowOverwrite)
                );

                document.setProcessedAt(Instant.now());
                completeProcessingRun(processingRun);
                log.info(
                    "Document processing completed successfully: documentId={}, elapsedMs={}",
                    documentId,
                    LogSanitizer.elapsedMillis(startNanos)
                );
                workflow.success();
                return setStatus(document, DocumentStatus.COMPLETED, null);
            } catch (Exception ex) {
                failProcessingRun(processingRun, ex);
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

    public DocumentProcessingOptionsResponse getProcessingOptions(String documentId) {
        DocumentUploadNode document = documentUploadRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        return toProcessingOptionsResponse(document);
    }

    public DocumentProcessingOptionsResponse replaceProcessingDefaults(String documentId, Map<String, Object> options) {
        DocumentUploadNode document = documentUploadRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        DocumentFormatDetection detection = processingOptionsRegistry.detect(document.getOriginalFilename(), document.getContentType());
        Map<String, Object> normalized = processingOptionsRegistry.validate(detection, options);
        document.setProcessingDefaultsJson(writeJson(normalized));
        document.setProcessingDefaultsUpdatedAt(Instant.now());
        DocumentUploadNode saved = documentUploadRepository.save(document);
        return toProcessingOptionsResponse(saved);
    }

    public DocumentProcessingOptionsResponse clearProcessingDefaults(String documentId) {
        DocumentUploadNode document = documentUploadRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        processingOptionsRegistry.detect(document.getOriginalFilename(), document.getContentType());
        document.setProcessingDefaultsJson(null);
        document.setProcessingDefaultsUpdatedAt(null);
        DocumentUploadNode saved = documentUploadRepository.save(document);
        return toProcessingOptionsResponse(saved);
    }

    public List<DocumentChunkNode> getDocumentChunks(String documentId) {
        log.info("Loading document chunks: documentId={}", documentId);
        List<DocumentChunkNode> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        log.info("Document chunks loaded: documentId={}, count={}", documentId, chunks.size());
        return chunks;
    }

    private ParsedDocument parseDocument(DocumentUploadNode document, Map<String, Object> effectiveOptions) throws IOException {
        byte[] bytes = documentUploadService.readContent(document.getContentUri());
        log.info("Loaded document bytes from storage: documentId={}, bytes={}", document.getId(), bytes.length);
        return documentParsingService.parseStructured(
            document.getOriginalFilename(),
            document.getContentType(),
            bytes,
            effectiveOptions
        );
    }

    private List<ChunkWithMetadata> chunksFor(
        DocumentUploadNode document,
        DocumentProcessingRunNode processingRun,
        ParsedDocument parsedDocument
    ) {
        List<ChunkWithMetadata> chunks = new java.util.ArrayList<>();
        for (ParsedSection section : parsedDocument.sections()) {
            List<String> sectionChunks = chunkingService.split(section.text());
            for (String text : sectionChunks) {
                chunks.add(new ChunkWithMetadata(text, chunkMetadata(document, processingRun, parsedDocument, section)));
            }
        }
        return chunks;
    }

    private Map<String, Object> chunkMetadata(
        DocumentUploadNode document,
        DocumentProcessingRunNode processingRun,
        ParsedDocument parsedDocument,
        ParsedSection section
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", document.getOriginalFilename());
        metadata.put("parserId", section.parserId() == null ? parsedDocument.parserId() : section.parserId());
        metadata.put("format", section.format() == null ? parsedDocument.format() : section.format());
        metadata.put("processingRunId", processingRun.getId());
        metadata.put("sectionIndex", section.sectionIndex());
        if (section.pageNumber() != null) {
            metadata.put("pageNumber", section.pageNumber());
        }
        if (section.pageCount() != null) {
            metadata.put("pageCount", section.pageCount());
        }
        Map<String, Object> parserMetadata = new LinkedHashMap<>();
        parserMetadata.putAll(parsedDocument.metadata());
        parserMetadata.putAll(section.metadata());
        if (!parserMetadata.isEmpty()) {
            metadata.put("parserMetadata", parserMetadata);
        }
        return metadata;
    }

    private DocumentProcessingOptionSet resolveOptionSet(DocumentUploadNode document, Map<String, Object> requestedOptions) {
        DocumentFormatDetection detection = processingOptionsRegistry.detect(document.getOriginalFilename(), document.getContentType());
        Map<String, Object> savedDefaults = readSavedDefaults(document, detection);
        Map<String, Object> normalizedRequestedOptions = processingOptionsRegistry.validate(detection, requestedOptions);
        Map<String, Object> effectiveOptions = processingOptionsRegistry.merge(
            detection,
            savedDefaults,
            normalizedRequestedOptions
        );
        return new DocumentProcessingOptionSet(
            detection,
            normalizedRequestedOptions,
            savedDefaults,
            effectiveOptions
        );
    }

    private Map<String, Object> readSavedDefaults(DocumentUploadNode document, DocumentFormatDetection detection) {
        Map<String, Object> savedDefaults = readJsonMap(document.getProcessingDefaultsJson());
        return processingOptionsRegistry.validate(detection, savedDefaults);
    }

    private DocumentProcessingOptionsResponse toProcessingOptionsResponse(DocumentUploadNode document) {
        DocumentFormatDetection detection = processingOptionsRegistry.detect(document.getOriginalFilename(), document.getContentType());
        Map<String, Object> savedDefaults = readSavedDefaults(document, detection);
        List<DocumentProcessingOptionResponse> optionResponses = processingOptionsRegistry.applicableDefinitions(detection).stream()
            .map(definition -> new DocumentProcessingOptionResponse(
                definition.key(),
                definition.label(),
                definition.valueType().name(),
                definition.defaultValue(),
                savedDefaults.get(definition.key()),
                new DocumentProcessingOptionConstraintResponse(
                    definition.constraints().min(),
                    definition.constraints().max(),
                    definition.constraints().allowedValues()
                ),
                definition.parserIds(),
                definition.fileFormats(),
                definition.mutable(),
                definition.description()
            ))
            .toList();
        return new DocumentProcessingOptionsResponse(
            document.getId(),
            detection.parserId(),
            detection.fileFormat(),
            savedDefaults,
            document.getProcessingDefaultsUpdatedAt(),
            optionResponses
        );
    }

    private DocumentProcessingRunNode createProcessingRun(DocumentUploadNode document, DocumentProcessingOptionSet optionSet) {
        DocumentProcessingRunNode run = new DocumentProcessingRunNode();
        run.setId(UUID.randomUUID().toString());
        run.setDocumentId(document.getId());
        run.setKnowledgeBaseId(document.getKnowledgeBaseId());
        run.setSourceSha256(document.getSha256());
        run.setParserId(optionSet.detection().parserId());
        run.setFileFormat(optionSet.detection().fileFormat());
        run.setRequestedOptionsJson(writeJson(optionSet.requestedOptions()));
        run.setSavedDefaultsJson(writeJson(optionSet.savedDefaults()));
        run.setEffectiveOptionsJson(writeJson(optionSet.effectiveOptions()));
        run.setStatus(DocumentProcessingRunStatus.RUNNING);
        run.setStage("STARTED");
        run.setStartedAt(Instant.now());
        run.setActiveCompleted(false);
        DocumentProcessingRunNode saved = documentProcessingRunRepository.save(run);
        documentProcessingRunRepository.attachToDocument(document.getId(), saved.getId());
        return saved;
    }

    private void completeProcessingRun(DocumentProcessingRunNode run) {
        run.setStatus(DocumentProcessingRunStatus.COMPLETED);
        run.setStage("COMPLETED");
        run.setCompletedAt(Instant.now());
        run.setErrorMessage(null);
        run.setActiveCompleted(true);
        DocumentProcessingRunNode saved = documentProcessingRunRepository.save(run);
        documentProcessingRunRepository.deactivateOtherCompletedRuns(saved.getDocumentId(), saved.getId());
    }

    private void failProcessingRun(DocumentProcessingRunNode run, Exception ex) {
        run.setStatus(DocumentProcessingRunStatus.FAILED);
        run.setStage("FAILED");
        run.setCompletedAt(Instant.now());
        run.setErrorMessage(ex.getMessage());
        run.setActiveCompleted(false);
        documentProcessingRunRepository.save(run);
    }

    private void updateProcessingRunStage(DocumentProcessingRunNode run, String stage) {
        run.setStage(stage);
        documentProcessingRunRepository.save(run);
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Stored document processing defaults JSON is invalid", ex);
        }
    }

    private String writeJson(Map<String, Object> options) {
        try {
            return objectMapper.writeValueAsString(options == null ? Map.of() : options);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Document processing options JSON is invalid", ex);
        }
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

    private record ChunkWithMetadata(String text, Map<String, Object> metadata) {
    }
}
