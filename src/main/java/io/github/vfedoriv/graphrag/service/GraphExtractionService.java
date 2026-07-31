package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.application.processing.ExtractionRunLifecycle;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import io.github.vfedoriv.graphrag.graph.GraphExtractionClient;
import io.github.vfedoriv.graphrag.graph.GraphExtractionResult;
import io.github.vfedoriv.graphrag.graph.GraphExtractionValidationService;
import io.github.vfedoriv.graphrag.graph.GraphWriteService;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.observability.AiWorkflowContext;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GraphExtractionService {

    private final ActiveSchemaResolver activeSchemaResolver;
    private final ExtractionRunLifecycle extractionRunLifecycle;
    private final GraphExtractionValidationService validationService;
    private final GraphWriteService graphWriteService;
    private final ObjectProvider<GraphExtractionClient> graphExtractionClientProvider;
    private final GraphArtifactCleanupService graphArtifactCleanupService;
    private final AiObservationService aiObservationService;

    public GraphExtractionService(
        ActiveSchemaResolver activeSchemaResolver,
        ExtractionRunLifecycle extractionRunLifecycle,
        GraphExtractionValidationService validationService,
        GraphWriteService graphWriteService,
        ObjectProvider<GraphExtractionClient> graphExtractionClientProvider,
        GraphArtifactCleanupService graphArtifactCleanupService,
        AiObservationService aiObservationService
    ) {
        this.activeSchemaResolver = activeSchemaResolver;
        this.extractionRunLifecycle = extractionRunLifecycle;
        this.validationService = validationService;
        this.graphWriteService = graphWriteService;
        this.graphExtractionClientProvider = graphExtractionClientProvider;
        this.graphArtifactCleanupService = graphArtifactCleanupService;
        this.aiObservationService = aiObservationService;
    }

    public void extract(DocumentUploadNode document, List<DocumentChunkNode> chunks, boolean allowOverwrite) {
        long startNanos = System.nanoTime();
        requireConsistentScope(document, chunks);
        log.info(
            "Graph extraction starting: documentId={}, knowledgeBaseId={}, chunks={}",
            document.getId(),
            document.getKnowledgeBaseId(),
            chunks.size()
        );
        ActiveSchemaContext schemaContext = activeSchemaResolver.resolve(document.getKnowledgeBaseId());
        SchemaDocument schema = schemaContext.schema();

        GraphExtractionClient client = resolveGraphExtractionClient();
        if (client == null) {
            log.error("Graph extraction client is missing: documentId={}", document.getId());
            throw new IllegalStateException("Graph extraction model is not configured for this profile");
        }
        log.info("Graph extraction client resolved: class={}", client.getClass().getName());

        ExtractionRunNode run = extractionRunLifecycle.start(
            document.getId(),
            schemaContext.schemaDefinitionId(),
            "chat:" + schemaContext.schemaDefinition().getName()
        );
        log.info("Extraction run created: runId={}, schemaId={}, model={}", run.getId(), run.getSchemaId(), run.getModel());
        try (AiObservationScope workflow = aiObservationService.startWorkflow(new AiWorkflowContext(
            AiObservationService.WORKFLOW_GRAPH_EXTRACTION,
            schema.name(),
            Map.of(
                "document.id", String.valueOf(document.getId()),
                "knowledge_base.id", String.valueOf(document.getKnowledgeBaseId()),
                "schema.id", String.valueOf(schemaContext.schemaDefinitionId()),
                "extraction_run.id", String.valueOf(run.getId()),
                "document.chunk_count", String.valueOf(chunks.size())
            )
        ))) {
            try {
                for (int i = 0; i < chunks.size(); i++) {
                    DocumentChunkNode chunk = chunks.get(i);
                    log.info(
                        "Extracting chunk: runId={}, chunkId={}, chunkIndex={}/{} textLength={}",
                        run.getId(),
                        chunk.getId(),
                        i + 1,
                        chunks.size(),
                        chunk.getText() == null ? 0 : chunk.getText().length()
                    );
                    GraphExtractionResult result = client.extract(schema, chunk.getText());
                    log.info(
                        "Chunk extraction returned payload: runId={}, chunkId={}, nodes={}, relationships={}",
                        run.getId(),
                        chunk.getId(),
                        result.nodes().size(),
                        result.relationships().size()
                    );
                    GraphExtractionResult validatedResult = validationService.validate(result, schema);
                    workflow.highCardinalityAttribute("ai.graph.validated_nodes", String.valueOf(validatedResult.nodes().size()));
                    workflow.highCardinalityAttribute("ai.graph.validated_relationships", String.valueOf(validatedResult.relationships().size()));
                    graphWriteService.write(
                        document.getKnowledgeBaseId(),
                        run.getId(),
                        schemaContext.schemaDefinitionId(),
                        document.getId(),
                        chunk.getId(),
                        schema,
                        validatedResult
                    );
                }
                run = extractionRunLifecycle.complete(run);
                GraphArtifactCleanupService.ExtractionRunCleanupResult cleanupResult =
                    GraphArtifactCleanupService.ExtractionRunCleanupResult.zero();
                try {
                    cleanupResult = graphArtifactCleanupService.cleanupRunsAfterSuccessfulExtraction(
                        document.getId(),
                        run.getId(),
                        allowOverwrite
                    );
                } catch (Exception cleanupEx) {
                    log.error(
                        "Cleanup failed after successful extraction: runId={}, documentId={}, exceptionType={}",
                        run.getId(),
                        document.getId(),
                        LogMetadata.exceptionType(cleanupEx),
                        cleanupEx
                    );
                }
                log.info(
                    "Graph extraction completed: runId={}, documentId={}, chunks={}, allowOverwrite={}, deletedRuns={}, deletedRelationships={}, deletedObsoleteExtractedNodes={}, elapsedMs={}",
                    run.getId(),
                    document.getId(),
                    chunks.size(),
                    allowOverwrite,
                    cleanupResult.deletedRuns(),
                    cleanupResult.deletedRelationships(),
                    cleanupResult.deletedObsoleteExtractedNodes(),
                    LogMetadata.elapsedMillis(startNanos)
                );
                workflow.highCardinalityAttribute("ai.graph.deleted_runs", String.valueOf(cleanupResult.deletedRuns()));
                workflow.highCardinalityAttribute("ai.graph.deleted_relationships", String.valueOf(cleanupResult.deletedRelationships()));
                workflow.success();
            } catch (Exception ex) {
                workflow.error(ex);
                extractionRunLifecycle.fail(run, ex);
                log.error(
                    "Graph extraction failed: runId={}, documentId={}, elapsedMs={}, exceptionType={}",
                    run.getId(),
                    document.getId(),
                    LogMetadata.elapsedMillis(startNanos),
                    LogMetadata.exceptionType(ex),
                    ex
                );
                throw ex;
            }
        }
    }

    private GraphExtractionClient resolveGraphExtractionClient() {
        List<GraphExtractionClient> clients = graphExtractionClientProvider.orderedStream().toList();
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

    private void requireConsistentScope(DocumentUploadNode document, List<DocumentChunkNode> chunks) {
        if (document.getKnowledgeBaseId() == null || document.getKnowledgeBaseId().isBlank()) {
            throw new IllegalArgumentException("knowledgeBaseId must not be blank");
        }
        for (DocumentChunkNode chunk : chunks) {
            if (!document.getKnowledgeBaseId().equals(chunk.getKnowledgeBaseId())
                || !document.getId().equals(chunk.getDocumentId())) {
                throw new IllegalArgumentException("Chunk scope does not match the source document");
            }
        }
        boolean hasParents = chunks.stream().anyMatch(chunk -> "PARENT".equals(chunk.getKind()));
        if (hasParents && chunks.stream().anyMatch(chunk -> !"PARENT".equals(chunk.getKind()))) {
            throw new IllegalArgumentException("Hierarchical graph extraction must use persisted parents only");
        }
    }

}
