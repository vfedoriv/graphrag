package io.github.vfedoriv.graphrag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.application.processing.ChunkMetadataFactory;
import io.github.vfedoriv.graphrag.application.processing.ChunkPreparationStage;
import io.github.vfedoriv.graphrag.application.processing.EmbeddingPersistenceStage;
import io.github.vfedoriv.graphrag.application.processing.GraphExtractionStage;
import io.github.vfedoriv.graphrag.application.processing.PreparedChunk;
import io.github.vfedoriv.graphrag.application.processing.ProcessingJsonCodec;
import io.github.vfedoriv.graphrag.application.processing.ProcessingOptionResolver;
import io.github.vfedoriv.graphrag.application.processing.ProcessingRunLifecycle;
import io.github.vfedoriv.graphrag.application.processing.SourceParsingStage;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.document.ChunkingService;
import io.github.vfedoriv.graphrag.document.DocumentParsingService;
import io.github.vfedoriv.graphrag.document.ParsedDocument;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentProcessingRunNode;
import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.dto.DocumentProcessingOptionConstraintResponse;
import io.github.vfedoriv.graphrag.dto.DocumentProcessingOptionResponse;
import io.github.vfedoriv.graphrag.dto.DocumentProcessingOptionsResponse;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.infrastructure.persistence.DocumentChunkPersistenceAdapter;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.ExtractionRunRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DocumentProcessingService {

    private final DocumentUploadRepository documentUploadRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ExtractionRunRepository extractionRunRepository;
    private final ChunkingService chunkingService;
    private final DocumentProcessingOptionsRegistry processingOptionsRegistry;
    private final AiObservationService aiObservationService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    private final ProcessingJsonCodec processingJsonCodec;
    private final ProcessingOptionResolver processingOptionResolver;
    private final SourceParsingStage sourceParsingStage;
    private final ChunkPreparationStage chunkPreparationStage;
    private final ProcessingRunLifecycle processingRunLifecycle;
    private final EmbeddingPersistenceStage embeddingPersistenceStage;
    private final GraphExtractionStage graphExtractionStage;

    @Autowired
    public DocumentProcessingService(
        DocumentUploadRepository documentUploadRepository,
        DocumentChunkRepository documentChunkRepository,
        ExtractionRunRepository extractionRunRepository,
        DocumentUploadService documentUploadService,
        DocumentParsingService documentParsingService,
        ChunkingService chunkingService,
        DocumentProcessingOptionsRegistry processingOptionsRegistry,
        ProfileScopedAiClientResolver aiClientResolver,
        ObjectMapper objectMapper,
        GraphExtractionService graphExtractionService,
        AiObservationService aiObservationService,
        KnowledgeBaseService knowledgeBaseService,
        KnowledgeBaseLifecycleService knowledgeBaseLifecycleService,
        EmbeddingSpacePolicy embeddingSpacePolicy,
        ProcessingRunLifecycle processingRunLifecycle,
        DocumentChunkPersistenceAdapter chunkPersistenceAdapter
    ) {
        this.documentUploadRepository = documentUploadRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.extractionRunRepository = extractionRunRepository;
        this.chunkingService = chunkingService;
        this.processingOptionsRegistry = processingOptionsRegistry;
        this.aiObservationService = aiObservationService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeBaseLifecycleService = knowledgeBaseLifecycleService;
        this.processingJsonCodec = new ProcessingJsonCodec(objectMapper);
        this.processingOptionResolver = new ProcessingOptionResolver(processingOptionsRegistry, processingJsonCodec);
        this.sourceParsingStage = new SourceParsingStage(documentUploadService, documentParsingService);
        this.chunkPreparationStage = new ChunkPreparationStage(chunkingService, new ChunkMetadataFactory());
        this.processingRunLifecycle = processingRunLifecycle;
        this.embeddingPersistenceStage = new EmbeddingPersistenceStage(
            aiClientResolver, embeddingSpacePolicy, chunkPersistenceAdapter, chunkingService, processingJsonCodec
        );
        this.graphExtractionStage = new GraphExtractionStage(graphExtractionService);
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
        knowledgeBaseLifecycleService.requireManaged(document.getKnowledgeBaseId());
        DocumentProcessingOptionSet optionSet = processingOptionResolver.resolve(document, requestedOptions);
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
            DocumentProcessingRunNode processingRun = processingRunLifecycle.start(document, optionSet);
            try {
                processingRun = processingRunLifecycle.checkpoint(processingRun, "PARSING");
                document = setStatus(document, DocumentStatus.PARSING, null);
                ParsedDocument parsedDocument = sourceParsingStage.parse(document, optionSet.effectiveOptions());
                processingRun = processingRunLifecycle.checkpoint(processingRun, "CHUNKING");
                List<PreparedChunk> chunks = chunkPreparationStage.prepare(document, processingRun, parsedDocument);
                workflow.highCardinalityAttribute("document.chunk_count", String.valueOf(chunks.size()));
                log.info("Document parsed and chunked: documentId={}, chunks={}", documentId, chunks.size());
                processingRun = processingRunLifecycle.checkpoint(processingRun, "EMBEDDING");
                document = setStatus(document, DocumentStatus.EMBEDDING, null);

                AiProfileNode activeProfile = activeProfile(document.getKnowledgeBaseId());
                List<DocumentChunkNode> persistedChunks = embeddingPersistenceStage.execute(document, activeProfile, chunks);
                document = setStatus(document, DocumentStatus.EXTRACTING_GRAPH, null);
                processingRun = processingRunLifecycle.checkpoint(processingRun, "EXTRACTING_GRAPH");
                graphExtractionStage.execute(document, persistedChunks, activeProfile, allowOverwrite);

                document.setProcessedAt(Instant.now());
                processingRunLifecycle.complete(processingRun);
                log.info(
                    "Document processing completed successfully: documentId={}, elapsedMs={}",
                    documentId,
                    LogMetadata.elapsedMillis(startNanos)
                );
                workflow.success();
                return setStatus(document, DocumentStatus.COMPLETED, null);
            } catch (Exception ex) {
                processingRunLifecycle.fail(processingRun, ex);
                workflow.error(ex);
                log.error(
                    "Document processing failed: documentId={}, elapsedMs={}, exceptionType={}",
                    documentId,
                    LogMetadata.elapsedMillis(startNanos),
                    LogMetadata.exceptionType(ex),
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
        document.setProcessingDefaultsJson(processingJsonCodec.writeMap(normalized));
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

    private DocumentProcessingOptionsResponse toProcessingOptionsResponse(DocumentUploadNode document) {
        DocumentFormatDetection detection = processingOptionsRegistry.detect(document.getOriginalFilename(), document.getContentType());
        Map<String, Object> savedDefaults = processingOptionResolver.savedDefaults(document, detection);
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

    private DocumentUploadNode setStatus(DocumentUploadNode document, DocumentStatus status, String errorMessage) {
        log.info("Updating document status: documentId={}, status={}", document.getId(), status);
        document.setStatus(status);
        document.setErrorMessage(errorMessage);
        return documentUploadRepository.save(document);
    }

    private AiProfileNode activeProfile(String knowledgeBaseId) {
        return knowledgeBaseService.activeAiProfile(knowledgeBaseId);
    }

}
